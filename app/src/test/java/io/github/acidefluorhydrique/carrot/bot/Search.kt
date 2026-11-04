// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot.bot

import io.github.acidefluorhydrique.carrot.TargetMode
import kotlin.math.abs
import kotlin.random.Random

/**
 * 在計畫空間裡找更好的打法。
 *
 * 用 late acceptance hill climbing：新計畫只要不比「L 步之前的分數」差就接受。
 * 和模擬退火一樣能暫時走下坡、跳出局部最佳，但不用調溫度 —— 分數的尺度在過關與否之間差好幾個數量級，
 * 溫度很難定。每次評估都是完整跑一局真正的遊戲邏輯。
 */
class Search(
    private val target: Target,
    private val geo: Geometry,
    private val rng: Random,
    private val log: (String) -> Unit
) {

    var evaluations = 0
        private set

    /**
     * 打一局，打分。無盡模式每一局的波次順序是隨機的，同一份打法要在 [ENDLESS_SEEDS] 每一個種子上都打一次，
     * 取最差的那一局：找的是不靠運氣也撐得到的打法，而不是剛好合某一個種子的。
     */
    fun evaluate(plan: Plan): Outcome {
        if (!target.endless) {
            evaluations++
            return Bot(plan).play(target)
        }
        return ENDLESS_SEEDS.map { seed ->
            evaluations++
            Bot(plan).play(target.copy(seed = seed))
        }.minByOrNull { it.score }!!
    }

    fun run(seeds: List<Plan>, deadline: Long): Pair<Plan, Outcome> {
        var best = seeds.first()
        var bestOutcome = evaluate(best)
        for (seed in seeds.drop(1)) {
            val outcome = evaluate(seed)
            if (outcome.score > bestOutcome.score) {
                best = seed
                bestOutcome = outcome
            }
        }
        log("best seed: ${describe(bestOutcome)}")

        var current = best
        var currentScore = bestOutcome.score
        val history = DoubleArray(if (target.endless) 30 else 200) { currentScore }
        var sinceImprovement = 0
        var k = 0
        while (System.currentTimeMillis() < deadline) {
            val candidate = mutate(current)
            val outcome = evaluate(candidate)
            val score = outcome.score
            val slot = k % history.size
            if (score >= history[slot] || score >= currentScore) {
                current = candidate
                currentScore = score
            }
            history[slot] = currentScore
            k++
            sinceImprovement++
            if (score > bestOutcome.score) {
                best = candidate
                bestOutcome = outcome
                sinceImprovement = 0
                log("eval $evaluations: ${describe(outcome)}")
            }
            // 已經滿血過關，剩下只是在省錢；很久沒進步就收工，把時間留給別的關卡。
            // 過關但沒滿血的也一樣，只是多給一些時間找多一滴血
            if (bestOutcome.won && bestOutcome.carrotHp >= bestOutcome.maxCarrotHp && sinceImprovement > 4000) break
            if (bestOutcome.won && sinceImprovement > 30000) break
        }
        return best to bestOutcome
    }

    fun describe(o: Outcome): String =
        if (o.won) "WIN carrot ${o.carrotHp}/${o.maxCarrotHp} invested ${o.invested}"
        else if (o.endless) "lost at wave ${o.wave} (kills ${o.kills}, boss ${"%.0f".format(o.bossDamage * 100)}%, nukes ${o.nukes}, freezes ${o.freezes})"
        else "lost at wave ${o.wave}, leaked ${o.leaked}/${o.maxCarrotHp} (kills ${o.kills}, boss ${"%.0f".format(o.bossDamage * 100)}%)"

    // ---- 突變 ----

    fun mutate(plan: Plan): Plan {
        val steps = ArrayList(plan.steps)
        var call = plan.call
        var nukeAt = plan.nukeAt
        var freezeAt = plan.freezeAt
        var salvage = plan.salvage
        repeat(1 + rng.nextInt(3)) {
            // 固定陣型：只能調順序、升級、瞄準與兩條臨場規則，不能換塔、搬塔、加塔、拆塔
            val op = ops.random(rng)
            when (op) {
                0 -> retype(steps)
                1, 2 -> move(steps)
                3 -> swapNearby(steps)
                4 -> relocate(steps)
                5 -> insertBuild(steps)
                6 -> if (target.layout != null) deleteUpgrade(steps) else delete(steps)
                7 -> insertUpgrade(steps)
                8 -> retarget(steps)
                9 -> relocate(steps, upgradesOnly = true)
                10 -> when (if (target.endless) rng.nextInt(3) else 2) {
                    0 -> nukeAt = (nukeAt + (rng.nextFloat() - 0.5f) * 0.2f).coerceIn(0.3f, 1f)
                    1 -> freezeAt = (freezeAt + (rng.nextFloat() - 0.5f) * 0.2f).coerceIn(0.2f, 1f)
                    else -> call = if (call == CallPolicy.NEVER) CallPolicy.WHEN_CLEAR else CallPolicy.NEVER
                }
                11 -> salvage = !salvage
                12 -> swapCells(steps)
            }
        }
        return Plan(tidy(steps), call, nukeAt, salvage, freezeAt)
    }

    /**
     * 能用的突變，對應 [mutate] 裡的編號。固定陣型只調順序與臨場規則；
     * 可換塔的陣型多了換塔種與兩座塔互換位置，格子本身不動。
     */
    private val ops: List<Int> = when {
        target.layout != null && target.retypeable -> FIXED_LAYOUT_OPS + listOf(0, 12, 12)
        target.layout != null -> FIXED_LAYOUT_OPS
        target.endless -> (0..10).toList() + 12
        else -> (0..12).toList()
    }

    /** 兩座不同種的塔互換位置，升級跟著塔走。試的是「哪種塔擺在哪段路」的搭配，不改變花費。 */
    private fun swapCells(steps: MutableList<Step>) {
        val builds = buildIndices(steps)
        if (builds.size < 2) return
        val i = builds.random(rng)
        val a = steps[i] as Build
        val others = builds.filter { (steps[it] as Build).type != a.type }
        val j = others.randomOrNull(rng) ?: return
        val b = steps[j] as Build
        steps[i] = a.copy(col = b.col, row = b.row)
        steps[j] = b.copy(col = a.col, row = a.row)
    }

    private fun buildIndices(steps: List<Step>): List<Int> = steps.indices.filter { steps[it] is Build }

    private fun retype(steps: MutableList<Step>) {
        val i = buildIndices(steps).randomOrNull(rng) ?: return
        val b = steps[i] as Build
        steps[i] = b.copy(type = geo.types.random(rng))
    }

    private fun retarget(steps: MutableList<Step>) {
        val i = buildIndices(steps).randomOrNull(rng) ?: return
        val b = steps[i] as Build
        steps[i] = b.copy(mode = TargetMode.values().random(rng))
    }

    private fun freeCell(steps: List<Step>, near: Pair<Int, Int>?): Pair<Int, Int>? {
        val used = steps.filterIsInstance<Build>().mapTo(HashSet()) { it.col to it.row }
        val free = geo.cells.filter { it !in used }
        if (free.isEmpty()) return null
        if (near != null && rng.nextFloat() < 0.6f) {
            val close = free.filter { abs(it.first - near.first) <= 2 && abs(it.second - near.second) <= 2 }
            if (close.isNotEmpty()) return close.random(rng)
        }
        return free.random(rng)
    }

    private fun move(steps: MutableList<Step>) {
        val i = buildIndices(steps).randomOrNull(rng) ?: return
        val b = steps[i] as Build
        val cell = freeCell(steps, b.col to b.row) ?: return
        steps[i] = b.copy(col = cell.first, row = cell.second)
    }

    private fun swapNearby(steps: MutableList<Step>) {
        if (steps.size < 2) return
        val i = rng.nextInt(steps.size - 1)
        val j = (i + 1 + rng.nextInt(3)).coerceAtMost(steps.size - 1)
        val t = steps[i]
        steps[i] = steps[j]
        steps[j] = t
    }

    /** 把一個動作搬到別的位置：提早或延後。 */
    private fun relocate(steps: MutableList<Step>, upgradesOnly: Boolean = false) {
        val candidates = if (upgradesOnly) steps.indices.filter { steps[it] is Upgrade } else steps.indices.toList()
        val i = candidates.randomOrNull(rng) ?: return
        val step = steps.removeAt(i)
        steps.add(rng.nextInt(steps.size + 1), step)
    }

    private fun insertBuild(steps: MutableList<Step>) {
        val cell = freeCell(steps, null) ?: return
        val id = (steps.filterIsInstance<Build>().maxOfOrNull { it.id } ?: 0) + 1
        val at = rng.nextInt(steps.size + 1)
        steps.add(at, Build(id, cell.first, cell.second, geo.types.random(rng), TargetMode.FIRST))
        repeat(rng.nextInt(3)) { steps.add(rng.nextInt(at + 1, steps.size + 1), Upgrade(id)) }
    }

    private fun delete(steps: MutableList<Step>) {
        if (steps.isEmpty()) return
        val step = steps.removeAt(rng.nextInt(steps.size))
        if (step is Build) steps.removeAll { it is Upgrade && it.buildId == step.id }
    }

    private fun deleteUpgrade(steps: MutableList<Step>) {
        val i = steps.indices.filter { steps[it] is Upgrade }.randomOrNull(rng) ?: return
        steps.removeAt(i)
    }

    private fun insertUpgrade(steps: MutableList<Step>) {
        val i = buildIndices(steps).randomOrNull(rng) ?: return
        val b = steps[i] as Build
        steps.add(rng.nextInt(i + 1, steps.size + 1), Upgrade(b.id))
    }

    /** 丟掉指向不存在的塔、或超過滿級次數的升級。 */
    private fun tidy(steps: List<Step>): List<Step> {
        val ids = steps.filterIsInstance<Build>().mapTo(HashSet()) { it.id }
        val counts = HashMap<Int, Int>()
        return steps.filter { step ->
            if (step !is Upgrade) return@filter true
            if (step.buildId !in ids) return@filter false
            val n = (counts[step.buildId] ?: 0) + 1
            counts[step.buildId] = n
            n <= io.github.acidefluorhydrique.carrot.Tower.MAX_LEVEL - 1
        }
    }

    companion object {
        /** 無盡模式用來評分的種子。固定下來，同一份打法每次的分數才一樣。 */
        val ENDLESS_SEEDS = listOf(1L, 2L, 3L)

        /** 固定陣型時可用的突變，對應 [mutate] 裡的編號。 */
        private val FIXED_LAYOUT_OPS = listOf(3, 4, 6, 7, 8, 9, 10)
    }
}
