// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 無盡模式。
 *
 * 玩家這一側有上限：格子就那麼多，每座塔最多三級。敵人沒有上限：
 * 血量每波 ×[HP_GROWTH]，所以每一局遲早會結束，成績就是撐到第幾波。
 * 擊殺金幣也會成長（×[REWARD_GROWTH]），否則玩家早在填滿格子之前就被金幣卡死，
 * 「上限是格子」就不成立了。清障物在這裡格外重要：每清一格就多一個建塔位，等於直接提高上限。
 *
 * tools/balance.py 的 `endless` 指令用同一套公式模擬：格子大約在第 25 波填滿，
 * 之後血量追上輸出，模型在第 40 多波結束。模型偏保守，實際應能再撐一些。
 * 結束的波次對格子數與金幣成長率都不敏感，主要由 [HP_GROWTH] 決定。
 *
 * 版圖借用 5-4：路徑最長、轉彎最多，火箭的直線和太陽的彎道都有發揮空間。
 * 全部八座塔開放。
 *
 * 數值改了，tools/balance.py 裡的 ENDLESS_* 常數也要跟著改。
 */
object Endless {

    /** 不在 [GameLevels.all] 裡，只用於存檔與 [GameLevels.byId]。一旦發佈就不要改。 */
    const val LEVEL_ID = 1000

    /** 通過 4-3 才開放：太陽在那一關登場，到那時八座塔都見過了。 */
    const val UNLOCK_AFTER_LEVEL = 15

    /** 借用版圖的關卡（5-4）。障礙物金幣也以它的進度計算。 */
    const val MAP_LEVEL_ID = 20

    const val START_GOLD = 350
    const val CARROT_HP = 10

    const val BASE_HP = 30f
    const val HP_GROWTH = 1.12f
    const val BASE_COUNT = 10f
    const val COUNT_GROWTH = 1.03f
    const val BASE_REWARD = 6f
    const val REWARD_GROWTH = 1.06f
    const val BASE_SPEED = 1.5f
    const val SPEED_STEP = 0.01f
    const val MAX_SPEED = 2.1f

    /** 單組敵人數量上限。低階機上同時畫太多敵人會掉幀。 */
    const val MAX_GROUP = 40

    /** 血量上限，遠超任何人撐得到的波次，只是避免 Int 溢位（首領還要再乘 9）。 */
    private const val MAX_HP = 200_000_000f

    /**
     * 十波一輪。每種新威脅先單獨出現，再和其他種類混編；每輪最後一波是首領。
     * 第二個數字是相對於該波基準數量的比例。
     */
    private val PATTERN: List<List<Pair<EnemyKind, Float>>> = listOf(
        listOf(EnemyKind.GRUNT to 1.0f),
        listOf(EnemyKind.RUNNER to 1.0f),
        listOf(EnemyKind.SWARM to 1.0f),
        listOf(EnemyKind.GRUNT to 0.6f, EnemyKind.RUNNER to 0.6f),
        listOf(EnemyKind.TANK to 1.0f),
        listOf(EnemyKind.SWARM to 0.7f, EnemyKind.GRUNT to 0.5f),
        listOf(EnemyKind.RUNNER to 0.7f, EnemyKind.TANK to 0.6f),
        listOf(EnemyKind.SWARM to 1.3f),
        listOf(EnemyKind.TANK to 0.7f, EnemyKind.GRUNT to 0.6f),
        listOf(EnemyKind.BOSS to 1.0f, EnemyKind.GRUNT to 0.7f)
    )

    private fun countFactor(kind: EnemyKind): Float = when (kind) {
        EnemyKind.SWARM -> 2.2f
        EnemyKind.TANK -> 0.45f
        else -> 1f
    }

    private fun spawnInterval(kind: EnemyKind): Int = when (kind) {
        EnemyKind.GRUNT -> 42
        EnemyKind.RUNNER -> 32
        EnemyKind.SWARM -> 14
        EnemyKind.TANK -> 56
        EnemyKind.BOSS -> 150
    }

    val level: LevelConfig by lazy {
        GameLevels.byId(MAP_LEVEL_ID).copy(
            id = LEVEL_ID,
            startGold = START_GOLD,
            carrotHp = CARROT_HP,
            allowedTowers = TowerType.values().toList(),
            // 只放第一波：ObstacleManager 依第一波推算障礙物強度。
            // 實際的波次由 EnemyManager 透過 [wave] 逐波取得。
            waves = listOf(wave(0))
        )
    }

    fun isUnlocked(completed: Set<Int>): Boolean = UNLOCK_AFTER_LEVEL in completed

    fun isEndless(level: LevelConfig): Boolean = level.id == LEVEL_ID

    /**
     * HUD 每一影格都會問目前與下一波的內容，記住最近算過的幾波，免得每幀重新配置。
     * 波次完全由 index 決定，所以存檔只要記 index。
     */
    private val cache = LinkedHashMap<Int, WaveConfig>()

    fun wave(index: Int): WaveConfig = synchronized(cache) {
        cache[index]?.let { return it }
        val built = build(index)
        cache[index] = built
        if (cache.size > 4) cache.remove(cache.keys.first())
        built
    }

    private fun build(index: Int): WaveConfig {
        val hp = min(BASE_HP * HP_GROWTH.pow(index), MAX_HP).roundToInt().coerceAtLeast(1)
        val count = BASE_COUNT * COUNT_GROWTH.pow(index)
        val reward = (BASE_REWARD * REWARD_GROWTH.pow(index)).roundToInt().coerceAtLeast(1)
        val speed = min(BASE_SPEED + SPEED_STEP * index, MAX_SPEED)
        val groups = PATTERN[index % PATTERN.size].map { (kind, weight) ->
            val n = if (kind.isBoss) 1 else (count * weight * countFactor(kind)).roundToInt().coerceIn(1, MAX_GROUP)
            WaveGroup(kind, n, hp, speed, reward, spawnInterval(kind))
        }
        return WaveConfig(groups)
    }
}
