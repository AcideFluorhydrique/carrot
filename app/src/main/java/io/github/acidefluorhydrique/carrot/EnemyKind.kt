// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

/**
 * 敵人種類。波次設定只描述「基礎強度曲線」，
 * 實際數值由種類倍率換算，方便統一調平衡。
 */
enum class EnemyKind(
    val displayNameRes: Int,
    val emoji: String,
    val hpMultiplier: Float,
    val speedMultiplier: Float,
    val rewardMultiplier: Float,
    val sizeScale: Float,
    /** 固定減傷，毒傷無視。 */
    val armor: Int,
    val auraColor: String,
    /** 漏怪時扣掉的蘿蔔血量。首領是上限，實際看剩多少血，見 [leakFor]。 */
    val leakDamage: Int = 1,
    val isBoss: Boolean = false
) {
    GRUNT(R.string.enemy_grunt, "👾", 1.0f, 1.0f, 1.0f, 1.0f, 0, "#A78BFA"),
    RUNNER(R.string.enemy_runner, "🐰", 0.6f, 1.55f, 0.95f, 0.85f, 0, "#38BDF8"),
    SWARM(R.string.enemy_swarm, "🐛", 0.42f, 1.2f, 0.5f, 0.72f, 0, "#A3E635"),
    TANK(R.string.enemy_tank, "🦏", 2.6f, 0.62f, 1.7f, 1.25f, 3, "#94A3B8", leakDamage = 2),
    BOSS(R.string.enemy_boss, "👹", 9.0f, 0.52f, 6.0f, 1.8f, 6, "#F87171", leakDamage = 5, isBoss = true);

    val displayName: String get() = Strings.get(displayNameRes)

    /**
     * 漏掉時實際扣的血。首領按剩下的血量比例扣，無條件進位、最少一滴：
     * 滿血漏掉扣滿，打掉一半扣 3，打到剩兩成以下只扣 1。
     * 否則打不死的首領打多少都一樣，玩家乾脆完全不理它。
     */
    fun leakFor(hpRatio: Float): Int =
        if (isBoss) kotlin.math.ceil(leakDamage * hpRatio.coerceIn(0f, 1f) - 1e-4f).toInt().coerceIn(1, leakDamage)
        else leakDamage

    /** 玩法說明用的一句話描述，護甲類需要帶入數值。 */
    val note: String
        get() = when (this) {
            GRUNT -> Strings.get(R.string.enemy_grunt_note)
            RUNNER -> Strings.get(R.string.enemy_runner_note)
            SWARM -> Strings.get(R.string.enemy_swarm_note)
            TANK -> Strings.format(R.string.enemy_tank_note, armor, leakDamage)
            BOSS -> Strings.format(R.string.enemy_boss_note, armor, leakDamage)
        }

    companion object {
        fun fromName(name: String): EnemyKind =
            values().firstOrNull { it.name == name } ?: GRUNT
    }
}
