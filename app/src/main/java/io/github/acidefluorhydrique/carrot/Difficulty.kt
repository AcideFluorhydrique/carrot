// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 難度。普通模式就是關卡資料本身，困難模式在同一份資料上加一層指數成長：
 * 第 w 波（0 起算）的血量乘 [hpGrowth]^w，數量乘 [countGrowth]^w。
 *
 * 為什麼是「乘在原曲線上」而不是換一條平滑的指數曲線：
 * 原本的波次本來就接近指數成長，而且有刻意的節奏 —— 中段一波喘息、最後一波首領尖峰。
 * 直接換曲線會把這些設計抹掉，乘上去則保留節奏，只讓壓力越往後越重。
 *
 * 為什麼第一波不動：普通模式的難度集中在開局，那時只有起始金可用；
 * 之後擊殺金幣滾雪球，後段幾乎都是在等。困難模式要壓的正是這段滑行，
 * 而第五章的開局本來就很緊，再加碼就打不過了。
 *
 * 數量和血量分開成長，因為兩者逼出的打法不同：血量只要求更多輸出，
 * 數量才會讓群傷、擺位與減速變得必要。數量放大時生成間隔同比縮短，
 * 每波的長度大致不變，敵人變得更密。首領不放大數量 —— 多一隻首領就是多 5 點漏怪傷，
 * 那是另一種難度，不是這裡要的。
 *
 * 擊殺金幣刻意不動：tools/balance.py 顯示降低金幣會先讓 4-4、5-2 打不過，
 * 卻還沒讓第一章變難，因為各章的餘裕差了好幾倍，全域倍率只能顧到一端。
 *
 * 數值改了，tools/balance.py 裡的 HARD_* 常數也要跟著改。
 */
enum class Difficulty(
    val labelRes: Int,
    val hpGrowth: Float,
    val countGrowth: Float
) {
    NORMAL(R.string.difficulty_normal, 1f, 1f),
    HARD(R.string.difficulty_hard, 1.15f, 1.08f);

    val label: String get() = Strings.get(labelRes)

    fun next(): Difficulty = values()[(ordinal + 1) % values().size]

    /** 「花園小徑 1-1 · 困難」；普通模式不加註記。 */
    fun decorate(title: String): String =
        if (this == NORMAL) title else Strings.format(R.string.level_with_difficulty, title, label)

    /**
     * 這一關在此難度下的版本。一律從 [GameLevels] 裡的原始資料重新推導，
     * 所以拿一個已經套過難度的關卡再套一次，也不會疊加。
     */
    fun apply(level: LevelConfig): LevelConfig {
        val base = GameLevels.byId(level.id)
        if (this == NORMAL) return base
        return base.copy(
            waves = base.waves.mapIndexed { index, wave ->
                val hpFactor = hpGrowth.pow(index)
                val countFactor = countGrowth.pow(index)
                wave.copy(groups = wave.groups.map { scale(it, hpFactor, countFactor) })
            }
        )
    }

    private fun scale(group: WaveGroup, hpFactor: Float, countFactor: Float): WaveGroup {
        val hp = (group.hp * hpFactor).roundToInt().coerceAtLeast(1)
        if (group.kind.isBoss) return group.copy(hp = hp)
        val count = (group.count * countFactor).roundToInt().coerceAtLeast(1)
        val interval = (group.spawnInterval * group.count / count.toFloat())
            .roundToInt()
            .coerceAtLeast(MIN_SPAWN_INTERVAL)
        return group.copy(hp = hp, count = count, spawnInterval = interval)
    }

    companion object {
        /** 生成間隔下限（影格）。現有資料最密的蟲群是 12，再擠就會疊成一團。 */
        const val MIN_SPAWN_INTERVAL = 8

        fun fromName(name: String?): Difficulty =
            values().firstOrNull { it.name == name } ?: NORMAL
    }
}
