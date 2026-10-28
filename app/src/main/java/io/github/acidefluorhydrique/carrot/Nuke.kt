// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import kotlin.math.pow

/**
 * 核彈 ☢️，只在無盡模式出現：消滅畫面上所有的敵人。
 *
 * 它是全遊戲唯一不隨敵人血量成長的東西 —— 敵人血量無限增加，「全部消滅」卻永遠有效。
 * 不加限制的話，一局能撐多久會變成取決於買得起幾次核彈，而不是佈陣。所以：
 *
 * - 每用一次價格乘 [PRICE_GROWTH]。塔不做遞增價格，是因為蓋塔是常態建設；
 *   核彈是緊急手段，遞增價格正是讓它保持「緊急」的方式。
 * - 冷卻以遊戲影格計算（[GameState.tickTimers] 每個模擬步扣一），3x 和 1x 的冷卻長度一致。
 * - 擊殺不給金幣，否則後期敵人值錢時，核彈會自己付清自己的價格。
 * - 不碰障礙物，否則它也是免費的金幣與建塔位。塔本來就沒有血量，不受影響。
 * - 首領不會被一鍵清掉：扣掉的比例是 [bossFraction]，最少一半。
 *
 * 劇情與困難模式的每一關都是用 tools/balance.py 算過的，核彈會讓那些數字失去意義，所以只放在無盡模式。
 */
object Nuke {

    const val BASE_PRICE = 800
    const val PRICE_GROWTH = 2.0

    /** 60 秒的遊戲時間。 */
    const val COOLDOWN_FRAMES = 3600

    /** 首領門檻 = 這次付的價格 × 此倍率。越貴的核彈也越有力，兩件事走同一條曲線。 */
    private const val BOSS_THRESHOLD_PER_GOLD = 3f

    /**
     * 發射一顆：付錢、記次數、進冷卻，再對場上每個活著的敵人套用 [Enemy.nuke]。
     * 冷卻中、買不起或場上沒有敵人時什麼都不做，回傳 false。特效與音效由呼叫端負責。
     */
    fun tryFire(enemies: List<Enemy>): Boolean {
        val price = price(GameState.nukeUses)
        val targets = enemies.filter { it.isAlive }
        if (GameState.nukeCooldown > 0 || GameState.gold < price || targets.isEmpty()) return false
        GameState.spendGold(price)
        GameState.nukeUses++
        GameState.nukeCooldown = COOLDOWN_FRAMES
        for (enemy in targets) enemy.nuke(bossFraction(price, enemy.maxHp))
        return true
    }

    fun price(uses: Int): Int {
        val value = BASE_PRICE * PRICE_GROWTH.pow(uses)
        return if (value >= Int.MAX_VALUE) Int.MAX_VALUE else value.toInt()
    }

    /**
     * 首領被扣掉的最大血量比例。血量低於門檻直接消滅；高於門檻時比例隨血量下降，但最少一半。
     */
    fun bossFraction(pricePaid: Int, bossMaxHp: Int): Float {
        if (bossMaxHp <= 0) return 1f
        return (pricePaid * BOSS_THRESHOLD_PER_GOLD / bossMaxHp).coerceIn(0.5f, 1f)
    }
}
