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
 *
 * 無盡模式裡計畫做完之後，剩下的錢自動拿去升級最便宜的塔。只看撐到第幾波時，
 * 搜尋會把「多升幾座小塔」這種不改變波數的升級刪掉，留著錢不花；真人不會這樣。
 */
data class Plan(val steps: List<Step>, val call: CallPolicy = CallPolicy.NEVER, val nukeAt: Float = 1f) {

    val builds: List<Build> get() = steps.filterIsInstance<Build>()

    fun text(): String = buildString {
        appendLine("call ${call.name}")
        appendLine("nuke $nukeAt")
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
            val steps = ArrayList<Step>()
            for (raw in text.lines()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val f = line.split(Regex("\\s+"))
                when (f[0]) {
                    "call" -> call = CallPolicy.valueOf(f[1])
                    "nuke" -> nukeAt = f[1].toFloat()
                    "build" -> steps += Build(f[1].toInt(), f[2].toInt(), f[3].toInt(), TowerType.valueOf(f[4]), TargetMode.valueOf(f[5]))
                    "upgrade" -> steps += Upgrade(f[1].toInt())
                    "mark" -> steps += Mark(f[1].toInt(), f[2].toInt())
                    else -> throw IllegalArgumentException("bad plan line: $raw")
                }
            }
            return Plan(steps, call, nukeAt)
        }
    }
}

data class Outcome(
    val won: Boolean,
    /** 結束時的波次：贏了是最後一波，輸了是死在第幾波，無盡模式到上限就停在那一波。 */
    val wave: Int,
    val carrotHp: Int,
    val maxCarrotHp: Int,
    val kills: Int,
    val frames: Int,
    val nukes: Int,
    val invested: Int,
    val gold: Int,
    val layout: List<String>,
    val log: List<String>
) {
    /** 越大越好。過關優先，再看蘿蔔剩幾滴血，最後花得越少越好；沒過就比撐到第幾波、殺了幾隻。 */
    val score: Double
        get() = if (won) 1e9 + carrotHp * 1e6 - invested else wave * 1e5 + kills

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
        val pending = ArrayList(plan.steps)
        val planned = plan.builds.mapTo(HashSet()) { it.id }
        val built = HashMap<Int, Build>()
        val dropped = HashSet<Int>()
        val log = ArrayList<String>()
        val pathPx = (sim.map.pathPoints.size - 1) * sim.map.cellSize

        fun note(text: String) {
            if (record) log += "wave %3d  %5.0fs  gold %6d  %s".format(GameState.wave, sim.frames / 60f, GameState.gold, text)
        }

        fun act() {
            if (target.endless && plan.nukeAt < 1f && GameState.nukeCooldown == 0) {
                val danger = sim.enemyManager.enemies.any { it.isAlive && it.distanceTravelled >= plan.nukeAt * pathPx }
                if (danger && sim.nuke()) note("NUKE #${GameState.nukeUses}")
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
        while (!sim.isOver) {
            if (sim.frames % reaction == 0) act()
            sim.step()
            if (target.endless && GameState.wave > maxWave) {
                capped = true
                break
            }
            if (sim.frames >= frameLimit) break
        }
        if (record) note(if (GameState.status == GameStatus.VICTORY) "VICTORY" else if (capped) "stopped at wave cap" else "DEFEAT")

        return Outcome(
            won = GameState.status == GameStatus.VICTORY,
            wave = GameState.wave,
            carrotHp = GameState.carrotHp,
            maxCarrotHp = GameState.maxCarrotHp,
            kills = GameState.kills,
            frames = sim.frames,
            nukes = GameState.nukeUses,
            invested = sim.towerManager.towers.sumOf { it.invested },
            gold = GameState.gold,
            layout = sim.layout(),
            log = log
        )
    }

    companion object {
        const val MAX_ENDLESS_WAVE = 150

        /** 一般關卡最長也不到這麼久，超過表示卡住了。 */
        const val MAX_LEVEL_FRAMES = 60 * 60 * 60
    }
}
