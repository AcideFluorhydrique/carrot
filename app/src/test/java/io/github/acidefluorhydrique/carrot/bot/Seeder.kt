// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot.bot

import io.github.acidefluorhydrique.carrot.GameMap
import io.github.acidefluorhydrique.carrot.LevelConfig
import io.github.acidefluorhydrique.carrot.TargetMode
import io.github.acidefluorhydrique.carrot.Tower
import io.github.acidefluorhydrique.carrot.TowerType
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 地圖的幾何資訊，給起點產生器和突變用。座標單位是格，格心在 .5。
 */
class Geometry(level: LevelConfig) {

    private val map = GameMap().also { it.loadLevel(level) }

    /** 這一關能用的塔。 */
    val types: List<TowerType> = TowerType.values().filter { level.id >= it.unlockAtLevel && it in level.allowedTowers }

    /** 可以蓋塔的格子，包含要先清掉的障礙物格。 */
    val cells: List<Pair<Int, Int>> = (0 until GameMap.ROWS).flatMap { row ->
        (0 until GameMap.COLS).mapNotNull { col ->
            val v = map.grid[row][col]
            if (v == GameMap.EMPTY || v == GameMap.OBSTACLE) col to row else null
        }
    }

    val obstacles: List<Pair<Int, Int>> = cells.filter { (col, row) -> map.grid[row][col] == GameMap.OBSTACLE }

    fun isObstacle(cell: Pair<Int, Int>): Boolean = map.grid[cell.second][cell.first] == GameMap.OBSTACLE

    /** 沿路徑每 1/4 格取一個點，代表敵人會經過的位置；點數也就約略等於停留時間。 */
    private val xs: FloatArray
    private val ys: FloatArray

    init {
        val path = level.path
        val px = ArrayList<Float>()
        val py = ArrayList<Float>()
        for (i in 0 until path.size - 1) {
            val (c0, r0) = path[i]
            val (c1, r1) = path[i + 1]
            for (k in 0 until SAMPLES_PER_CELL) {
                val t = k / SAMPLES_PER_CELL.toFloat()
                px += c0 + 0.5f + (c1 - c0) * t
                py += r0 + 0.5f + (r1 - r0) * t
            }
        }
        px += path.last().first + 0.5f
        py += path.last().second + 0.5f
        xs = px.toFloatArray()
        ys = py.toFloatArray()
    }

    val sampleCount: Int get() = xs.size

    /**
     * 這座塔放在這一格（滿級射程）能照顧到多少路徑，[weights] 是每個點目前還值多少。
     *
     * 一般的塔看射程圈裡有多少路徑：敵人在圈裡待越久，挨的打越多。夾在兩條平行路中間的格子
     * 一個圈罩兩條路，自然分數高。
     *
     * 火箭不一樣：射程只決定瞄得到誰，打出去會一路飛到地圖邊緣，沿途距離彈道 0.36 格內的都會中彈
     * （TowerManager.fireRocketAt）。所以它的分數是「瞄準射程內每個點時，彈道上有多少路徑」的平均 ——
     * 擺在長直線的延長線上、順著直線打，一發貫穿整條，分數最高。
     */
    fun score(type: TowerType, col: Int, row: Int, weights: FloatArray): Float {
        val ox = col + 0.5f
        val oy = row + 0.5f
        val range = type.rangeCells(Tower.MAX_LEVEL)
        val r2 = range * range
        if (type != TowerType.ROCKET) {
            var sum = 0f
            for (i in xs.indices) {
                val dx = xs[i] - ox
                val dy = ys[i] - oy
                if (dx * dx + dy * dy <= r2) sum += weights[i]
            }
            return sum
        }
        var total = 0f
        var aims = 0
        var i = 0
        while (i < xs.size) {
            val ax = xs[i] - ox
            val ay = ys[i] - oy
            val d2 = ax * ax + ay * ay
            if (d2 <= r2 && d2 > 1e-4f) {
                val d = sqrt(d2)
                val dx = ax / d
                val dy = ay / d
                var hits = 0f
                for (j in xs.indices) {
                    val qx = xs[j] - ox
                    val qy = ys[j] - oy
                    if (qx * dx + qy * dy > 0f && abs(qx * dy - qy * dx) <= ROCKET_HIT_CELLS) hits += weights[j]
                }
                total += hits
                aims++
            }
            i += 2
        }
        return if (aims == 0) 0f else total / aims
    }

    /** 蓋了一座塔之後，它射程內的路徑點貶值，下一座塔就會往沒人顧的地方放。 */
    fun cover(type: TowerType, col: Int, row: Int, weights: FloatArray) {
        val ox = col + 0.5f
        val oy = row + 0.5f
        val range = type.rangeCells(Tower.MAX_LEVEL)
        for (i in xs.indices) {
            val dx = xs[i] - ox
            val dy = ys[i] - oy
            if (dx * dx + dy * dy <= range * range) weights[i] *= COVER_DECAY
        }
    }

    companion object {
        private const val SAMPLES_PER_CELL = 4
        private const val ROCKET_HIT_CELLS = 0.36f
        private const val COVER_DECAY = 0.6f
    }
}

/** 產生搜尋的起點。起點只要「不笨」，好壞交給模擬和搜尋決定。 */
object Seeder {

    /** 每金幣大概值多少，只用來排起點，不必準。 */
    private val baseWeight = mapOf(
        TowerType.ARROW to 1.0f, TowerType.ICE to 0.35f, TowerType.BOMB to 0.9f, TowerType.MOON to 0.35f,
        TowerType.POISON to 0.7f, TowerType.ROCKET to 1.2f, TowerType.LIGHT to 0.8f, TowerType.SUN to 0.9f
    )

    fun seeds(geo: Geometry, endless: Boolean): List<Plan> {
        val plans = ArrayList<Plan>()
        val maxBuilds = if (endless) geo.cells.size else 40
        val nukeAt = if (endless) 0.85f else 1f
        for (marksFirst in listOf(true, false)) {
            plans += greedy(geo, baseWeight, maxBuilds, marksFirst, nukeAt)
        }
        for (favourite in geo.types) {
            val weights = baseWeight.mapValues { (type, w) -> if (type == favourite) w * 3f else w }
            plans += greedy(geo, weights, maxBuilds, marksFirst = true, nukeAt = nukeAt)
        }
        if (endless) plans += fromLayout(geo, PLAYER_ENDLESS_LAYOUT, nukeAt)
        return plans
    }

    /**
     * 貪心：每一步挑「這一格放這種塔」每金幣分數最高的組合，蓋下去之後它照顧到的路徑貶值。
     * 第三座塔之後，每蓋一座就排一次升級，最後把沒升滿的補上。
     */
    fun greedy(geo: Geometry, typeWeight: Map<TowerType, Float>, maxBuilds: Int, marksFirst: Boolean, nukeAt: Float): Plan {
        val weights = FloatArray(geo.sampleCount) { 1f }
        val used = HashSet<Pair<Int, Int>>()
        val steps = ArrayList<Step>()
        val upgrades = LinkedHashMap<Int, Int>()
        if (marksFirst) geo.obstacles.forEach { steps += Mark(it.first, it.second) }

        var id = 0
        while (id < maxBuilds) {
            var best: Pair<Int, Int>? = null
            var bestType: TowerType? = null
            var bestValue = 0f
            for (cell in geo.cells) {
                if (cell in used) continue
                val penalty = if (geo.isObstacle(cell)) 0.7f else 1f
                for (type in geo.types) {
                    val value = geo.score(type, cell.first, cell.second, weights) *
                        (typeWeight[type] ?: 1f) / type.baseCost * penalty
                    if (value > bestValue) {
                        bestValue = value
                        best = cell
                        bestType = type
                    }
                }
            }
            if (best == null || bestType == null) break
            id++
            used += best
            steps += Build(id, best.first, best.second, bestType, TargetMode.FIRST)
            geo.cover(bestType, best.first, best.second, weights)
            upgrades[id] = 0
            if (id >= 3) {
                val next = upgrades.entries.firstOrNull { it.value < 2 }
                if (next != null) {
                    steps += Upgrade(next.key)
                    next.setValue(next.value + 1)
                }
            }
        }
        for ((buildId, count) in upgrades) repeat(2 - count) { steps += Upgrade(buildId) }
        if (!marksFirst) geo.obstacles.forEach { steps += Mark(it.first, it.second) }
        return Plan(steps, CallPolicy.NEVER, nukeAt)
    }

    /**
     * 從一張畫好的佈局產生計畫：照貪心分數決定蓋的先後，每蓋三座就把最早那座升滿。
     * 塔種代號見 [Codes]，其他字元表示空著。
     */
    fun fromLayout(geo: Geometry, rows: List<String>, nukeAt: Float): Plan {
        val weights = FloatArray(geo.sampleCount) { 1f }
        val towers = ArrayList<Triple<Int, Int, TowerType>>()
        for ((row, line) in rows.withIndex()) {
            for ((col, ch) in line.withIndex()) {
                val type = Codes.typeOf(ch) ?: continue
                if ((col to row) in geo.cells && type in geo.types) towers += Triple(col, row, type)
            }
        }
        towers.sortByDescending { (col, row, type) -> geo.score(type, col, row, weights) / type.baseCost }

        val steps = ArrayList<Step>()
        geo.obstacles.forEach { steps += Mark(it.first, it.second) }
        val waiting = ArrayDeque<Int>()
        for ((index, tower) in towers.withIndex()) {
            val id = index + 1
            steps += Build(id, tower.first, tower.second, tower.third, TargetMode.FIRST)
            waiting += id
            if (id % 3 == 0) waiting.removeFirstOrNull()?.let { steps += Upgrade(it); steps += Upgrade(it) }
        }
        for (id in waiting) {
            steps += Upgrade(id)
            steps += Upgrade(id)
        }
        return Plan(steps, CallPolicy.NEVER, nukeAt)
    }

    /** 固定陣型的比較對象，見 [Target]。 */
    val LAYOUTS: Map<String, List<String>> by lazy {
        mapOf("player" to PLAYER_ENDLESS_LAYOUT, "bot" to BOT_ENDLESS_LAYOUT)
    }

    /**
     * 機器人第一次跑 120 分鐘找到的無盡模式陣型（第 50 波，4 顆核彈）。
     * 外圍與直路兩端放火箭，雙線夾縫混放太陽、炸彈、冰與月亮。
     */
    val BOT_ENDLESS_LAYOUT = listOf(
        "BR---LBRRIR---B#",
        "SS-I-A---PL-R---",
        "BB-S-S-I-BB-A--#",
        "IM-R-R-B-SM-B--R",
        "RL-M-S-S-RL-L--I",
        "#R-S-S-M-SS-I--R",
        "---I-R-R----RIMS",
        "#SMR---RPSSRLPRL"
    )

    /** 玩家自己排出來、撐到第 46 波的無盡模式陣型（1.2.2）。- 是路徑，# 是永久地形。 */
    val PLAYER_ENDLESS_LAYOUT = listOf(
        "RR---RRRRRR---R#",
        "RR-P-R---RR-R---",
        "RR-M-L-P-RP-M--#",
        "RB-S-R-M-LS-S--R",
        "RP-L-S-S-SM-S--R",
        "#R-M-R-L-RL-P--R",
        "---R-R-R----RRRR",
        "#RRR---RRRRRRRRR"
    )
}
