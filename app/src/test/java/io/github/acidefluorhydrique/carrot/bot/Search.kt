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

    fun evaluate(plan: Plan): Outcome {
        evaluations++
        return Bot(plan).play(target)
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
            // 已經滿血過關，剩下只是在省錢；很久沒進步就收工，把時間留給別的關卡
            if (bestOutcome.won && bestOutcome.carrotHp >= bestOutcome.maxCarrotHp && sinceImprovement > 4000) break
        }
        return best to bestOutcome
    }

    fun describe(o: Outcome): String =
        if (o.won) "WIN carrot ${o.carrotHp}/${o.maxCarrotHp} invested ${o.invested}"
        else "lost at wave ${o.wave} (kills ${o.kills}, nukes ${o.nukes})"

    // ---- 突變 ----

    fun mutate(plan: Plan): Plan {
        val steps = ArrayList(plan.steps)
        var call = plan.call
        var nukeAt = plan.nukeAt
        repeat(1 + rng.nextInt(3)) {
            when (rng.nextInt(11)) {
                0 -> retype(steps)
                1, 2 -> move(steps)
                3 -> swapNearby(steps)
                4 -> relocate(steps)
                5 -> insertBuild(steps)
                6 -> delete(steps)
                7 -> insertUpgrade(steps)
                8 -> retarget(steps)
                9 -> relocate(steps, upgradesOnly = true)
                10 -> if (target.endless && rng.nextBoolean()) {
                    nukeAt = (nukeAt + (rng.nextFloat() - 0.5f) * 0.2f).coerceIn(0.3f, 1f)
                } else {
                    call = if (call == CallPolicy.NEVER) CallPolicy.WHEN_CLEAR else CallPolicy.NEVER
                }
            }
        }
        return Plan(tidy(steps), call, nukeAt)
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
}
