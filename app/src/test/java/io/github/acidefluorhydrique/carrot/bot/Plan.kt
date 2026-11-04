// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot.bot

import io.github.acidefluorhydrique.carrot.GameState
import io.github.acidefluorhydrique.carrot.GameStatus
import io.github.acidefluorhydrique.carrot.TargetMode
import io.github.acidefluorhydrique.carrot.TowerType

sealed class Step

/** 蓋塔。[id] 讓升級指向「這一座」，塔被搬到別格時升級跟著走。 */
data class Build(val id: Int, val col: Int, val row: Int, val type: TowerType, val mode: TargetMode) : Step()

data class Upgrade(val buildId: Int) : Step()

/** 標記障礙物集火。 */
data class Mark(val col: Int, val row: Int) : Step()

enum class CallPolicy { NEVER, WHEN_CLEAR }

/**
 * 機器人玩家的打法：一串依序執行的動作，加上兩條固定的臨場規則。
 *
 * 執行方式（見 [Bot]）：從頭掃描，買得起就做，買不起就停下來存錢 —— 順序本身就是優先順序。
 * 蓋在障礙物上的塔先標記集火，等清掉再蓋，期間後面的動作照常進行。
 *
 * - [call]：場上清空時提前叫下一波，換提前獎勵金。
 * - [nukeAt]：無盡模式中，有敵人走完路徑的這個比例時發射核彈（買得起、沒在冷卻時）。1 表示不用核彈。
 * - [freezeAt]：同 [nukeAt]，換成冰彈。1 表示不用冰彈。
 * - [salvage]：一般關卡的最後一波生成完之後，所有敵人都已經走過的塔不會再開火，賣掉換錢升級還打得到的塔。
 *
 * 無盡模式裡計畫做完之後，剩下的錢自動拿去升級最便宜的塔。只看撐到第幾波時，
 * 搜尋會把「多升幾座小塔」這種不改變波數的升級刪掉，留著錢不花；真人不會這樣。
 */
data class Plan(
    val steps: List<Step>,
    val call: CallPolicy = CallPolicy.NEVER,
    val nukeAt: Float = 1f,
    val salvage: Boolean = false,
    val freezeAt: Float = 1f
) {

    val builds: List<Build> get() = steps.filterIsInstance<Build>()

    fun text(): String = buildString {
        appendLine("call ${call.name}")
        appendLine("nuke $nukeAt")
        appendLine("freeze $freezeAt")
        appendLine("salvage $salvage")
        for (step in steps) {
            appendLine(
                when (step) {
                    is Build -> "build ${step.id} ${step.col} ${step.row} ${step.type.name} ${step.mode.name}"
                    is Upgrade -> "upgrade ${step.buildId}"
                    is Mark -> "mark ${step.col} ${step.row}"
                }
            )
        }
    }

    companion object {
        fun parse(text: String): Plan {
            var call = CallPolicy.NEVER
            var nukeAt = 1f
            var freezeAt = 1f
            var salvage = false
            val steps = ArrayList<Step>()
            for (raw in text.lines()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val f = line.split(Regex("\\s+"))
                when (f[0]) {
                    "call" -> call = CallPolicy.valueOf(f[1])
                    "nuke" -> nukeAt = f[1].toFloat()
                    "freeze" -> freezeAt = f[1].toFloat()
                    "salvage" -> salvage = f[1].toBooleanStrict()
                    "build" -> steps += Build(f[1].toInt(), f[2].toInt(), f[3].toInt(), TowerType.valueOf(f[4]), TargetMode.valueOf(f[5]))
                    "upgrade" -> steps += Upgrade(f[1].toInt())
                    "mark" -> steps += Mark(f[1].toInt(), f[2].toInt())
                    else -> throw IllegalArgumentException("bad plan line: $raw")
                }
            }
            return Plan(steps, call, nukeAt, salvage, freezeAt)
        }
    }
}

data class Outcome(
    val won: Boolean,
    /** 結束時的波次：贏了是最後一波，輸了是死在第幾波，無盡模式到上限就停在那一波。 */
    val wave: Int,
    /** 一般關卡整局漏掉的蘿蔔血，包含蘿蔔歸零之後的（見 [Bot.play]）。無盡模式是死前漏掉的。 */
    val leaked: Int,
    val endless: Boolean,
    val carrotHp: Int,
    val maxCarrotHp: Int,
    val kills: Int,
    val frames: Int,
    val nukes: Int,
    val freezes: Int,
    /** 最後一波裡，首領被打掉的最大血量比例，0 到 1。 */
    val bossDamage: Float,
    val invested: Int,
    val gold: Int,
    val layout: List<String>,
    val log: List<String>
) {
    /**
     * 越大越好。過關優先，再看蘿蔔剩幾滴血，最後花得越少越好。
     *
     * 一般關卡沒過：比整局漏掉幾滴血。蘿蔔歸零後遊戲照跑，所以「漏 7 滴、差一滴就過」和「漏 30 滴」分得出來，
     * 「放走一隻首領、其他全殺」這種只差一點的打法也找得到。同分再比殺了幾隻、首領被打掉多少。
     *
     * 無盡模式沒有終點，照舊比撐到第幾波。
     */
    val score: Double
        get() = when {
            won -> 1e9 + carrotHp * 1e6 - invested
            endless -> wave * 1e5 + kills + bossDamage.coerceAtMost(0.999f)
            else -> 5e8 - leaked * 1e5 + kills + bossDamage.coerceAtMost(0.999f)
        }

    val stars: Int
        get() = when {
            !won || carrotHp <= 0 -> 0
            carrotHp >= maxCarrotHp -> 3
            carrotHp * 2 >= maxCarrotHp -> 2
            else -> 1
        }
}

/**
 * 照 [Plan] 打一局。
 *
 * [reaction] 是每隔幾影格才看一次場面、做一次決定。1 是機器的極限反應；
 * 用 30、60 重跑同一份計畫，可以看這份打法換成真人的手速還站不站得住。
 */
class Bot(private val plan: Plan, private val reaction: Int = 1) {

    fun play(target: Target, maxWave: Int = MAX_ENDLESS_WAVE, record: Boolean = false): Outcome {
        val sim = Sim(target)
        // 一般關卡用一顆打不死的蘿蔔跑完整局，再自己用真正的血量判輸贏：輸的打法也能比出「差多少」
        val realHp = GameState.maxCarrotHp
        if (!target.endless) GameState.carrotHp = SHADOW_HP
        fun leakedSoFar(): Int = if (target.endless) realHp - GameState.carrotHp else SHADOW_HP - GameState.carrotHp
        var diedAt = 0
        val lastWave = sim.level.waves.size
        val pending = ArrayList(plan.steps)
        val planned = plan.builds.mapTo(HashSet()) { it.id }
        val built = HashMap<Int, Build>()
        val dropped = HashSet<Int>()
        val log = ArrayList<String>()
        val pathPx = (sim.map.pathPoints.size - 1) * sim.map.cellSize

        fun note(text: String) {
            if (record) log += "wave %3d  %5.0fs  gold %6d  %s".format(GameState.wave, sim.frames / 60f, GameState.gold, text)
        }

        fun salvage() {
            if (!plan.salvage || target.endless || GameState.wave < lastWave || sim.enemyManager.pendingInWave > 0) return
            val alive = sim.enemyManager.enemies.filter { it.isAlive }
            if (alive.isEmpty()) return
            val rearmost = alive.minOf { it.distanceTravelled }
            for (tower in sim.towerManager.towers.toList()) {
                if (sim.lastReach(tower) < rearmost) {
                    val refund = tower.sellValue
                    if (sim.sell(tower)) note("sell ${tower.type.name} (${tower.col},${tower.row}) L${tower.level} +$refund, every enemy has passed it")
                }
            }
            // 錢先讓計畫裡還沒做的事用；計畫做完了，才升級還打得到人的塔，越下游的越先（剩下的敵人都會經過它）
            if (pending.any { it is Build || it is Upgrade }) return
            while (true) {
                val tower = sim.towerManager.towers
                    .filter { !it.isMaxLevel && sim.lastReach(it) >= rearmost }
                    .maxByOrNull { sim.lastReach(it) } ?: break
                if (GameState.gold < tower.upgradeCost || !sim.upgrade(tower)) break
                note("upgrade ${tower.type.name} (${tower.col},${tower.row}) -> L${tower.level} (salvaged gold)")
            }
        }

        fun act() {
            salvage()
            if (target.endless && plan.nukeAt < 1f && GameState.nukeCooldown == 0) {
                val danger = sim.enemyManager.enemies.any { it.isAlive && it.distanceTravelled >= plan.nukeAt * pathPx }
                if (danger && sim.nuke()) note("NUKE #${GameState.nukeUses}")
            }
            if (target.endless && plan.freezeAt < 1f && GameState.freezeCooldown == 0) {
                val danger = sim.enemyManager.enemies.any { it.isAlive && it.distanceTravelled >= plan.freezeAt * pathPx }
                if (danger && sim.freeze()) note("FREEZE #${GameState.freezeUses}")
            }

            var i = 0
            scan@ while (i < pending.size) {
                when (val step = pending[i]) {
                    is Mark -> {
                        sim.mark(step.col, step.row)
                        pending.removeAt(i)
                    }
                    is Build -> {
                        if (sim.obstacleManager.aliveAt(step.col, step.row) != null) {
                            sim.mark(step.col, step.row)
                            i++
                            continue@scan
                        }
                        if (!step.type.isAvailable || !sim.map.canPlaceTower(step.col, step.row)) {
                            dropped += step.id
                            pending.removeAt(i)
                            continue@scan
                        }
                        if (GameState.gold < step.type.baseCost) break@scan
                        if (sim.build(step.col, step.row, step.type, step.mode) != null) {
                            built[step.id] = step
                            note("build ${step.type.name} (${step.col},${step.row}) ${step.mode.name}")
                        } else {
                            dropped += step.id
                        }
                        pending.removeAt(i)
                    }
                    is Upgrade -> {
                        if (step.buildId in dropped || step.buildId !in planned) {
                            pending.removeAt(i)
                            continue@scan
                        }
                        val b = built[step.buildId]
                        if (b == null) {
                            i++
                            continue@scan
                        }
                        val tower = sim.towerAt(b.col, b.row)
                        if (tower == null || tower.isMaxLevel) {
                            pending.removeAt(i)
                            continue@scan
                        }
                        if (GameState.gold < tower.upgradeCost) break@scan
                        sim.upgrade(tower)
                        note("upgrade ${b.type.name} (${b.col},${b.row}) -> L${tower.level}")
                        pending.removeAt(i)
                    }
                }
            }

            if (target.endless && pending.none { it is Build || it is Upgrade }) {
                while (true) {
                    val tower = sim.towerManager.towers.filter { !it.isMaxLevel }.minByOrNull { it.upgradeCost } ?: break
                    if (GameState.gold < tower.upgradeCost || !sim.upgrade(tower)) break
                    note("upgrade ${tower.type.name} (${tower.col},${tower.row}) -> L${tower.level} (leftover gold)")
                }
            }

            if (plan.call == CallPolicy.WHEN_CLEAR && sim.enemyManager.enemies.none { it.isAlive } && sim.callNextWave()) {
                note("call wave ${GameState.wave}")
            }
        }

        val frameLimit = if (target.endless) Int.MAX_VALUE else MAX_LEVEL_FRAMES
        var capped = false
        var bossWave = 0
        var bossDamage = 0f
        while (!sim.isOver) {
            if (sim.frames % reaction == 0) act()
            val before = if (record) sim.enemyManager.enemies.filter { it.isAlive } else emptyList()
            sim.step()
            for (enemy in before) {
                if (enemy.hasReachedEnd) {
                    note("LEAK ${enemy.kind.name} -${enemy.kind.leakFor(enemy.hpRatio)}, carrot ${realHp - leakedSoFar()}/$realHp")
                }
            }
            if (diedAt == 0 && leakedSoFar() >= realHp) diedAt = GameState.wave
            if (GameState.wave != bossWave) {
                bossWave = GameState.wave
                bossDamage = 0f
            }
            for (enemy in sim.enemyManager.enemies) {
                if (enemy.kind.isBoss && enemy.isAlive) bossDamage = maxOf(bossDamage, 1f - enemy.hpRatio)
            }
            if (target.endless && GameState.wave > maxWave) {
                capped = true
                break
            }
            if (sim.frames >= frameLimit) break
        }
        val leaked = leakedSoFar()
        val won = GameState.status == GameStatus.VICTORY && leaked < realHp
        if (record) note(
            when {
                won -> "VICTORY"
                capped -> "stopped at wave cap"
                diedAt > 0 && !target.endless -> "DEFEAT (carrot ran out in wave $diedAt; $leaked leaked in all)"
                else -> "DEFEAT"
            }
        )

        return Outcome(
            won = won,
            wave = if (diedAt > 0) diedAt else GameState.wave,
            leaked = leaked,
            endless = target.endless,
            carrotHp = (realHp - leaked).coerceAtLeast(0),
            maxCarrotHp = GameState.maxCarrotHp,
            kills = GameState.kills,
            frames = sim.frames,
            nukes = GameState.nukeUses,
            freezes = GameState.freezeUses,
            bossDamage = bossDamage,
            invested = sim.spent,
            gold = GameState.gold,
            layout = sim.layout(),
            log = log
        )
    }

    companion object {
        const val MAX_ENDLESS_WAVE = 150

        /** 一般關卡模擬時的蘿蔔血。只是「不會死」，實際血量是關卡的 carrotHp。 */
        private const val SHADOW_HP = 1_000_000

        /** 一般關卡最長也不到這麼久，超過表示卡住了。 */
        const val MAX_LEVEL_FRAMES = 60 * 60 * 60
    }
}
