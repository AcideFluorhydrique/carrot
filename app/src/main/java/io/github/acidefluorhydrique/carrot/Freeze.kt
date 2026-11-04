// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import kotlin.math.pow

/**
 * 冰彈 ❄️，只在無盡模式出現：場上所有敵人原地凍住 [FREEZE_FRAMES]，解凍後再慢 [CHILL_FRAMES]。
 *
 * 和 [Nuke] 是一對：核彈換傷害，冰彈換時間。冰彈不殺任何東西，但凍住的時候塔照樣打，
 * 敵人在射程裡多待幾秒，就是多挨幾秒的打。首領要核彈還是冰彈，是玩家真正要想的事。
 *
 * - 首領和小怪一視同仁：凍一樣久、慢一樣久。核彈對首領打折，是因為「一鍵消滅」會取代佈陣；
 *   凍住只是爭取時間，傷害仍然要塔自己打出來，不需要打折。
 * - 價格、冷卻與核彈同一套做法：每用一次價格乘 [PRICE_GROWTH]，冷卻以遊戲影格計，3x 與 1x 一樣長。
 *   冷卻比首領走完全程還長，一隻首領最多吃一次。
 * - 解凍後的減速和冰塔、月亮的減速不疊加，取最強的那個，見 [Enemy.freeze]。
 * - 不碰障礙物，不給金幣。
 *
 * 和核彈一樣只放在無盡模式：劇情與困難模式的每一關都是用 tools/balance.py 算過的。
 */
object Freeze {

    const val BASE_PRICE = 400
    const val PRICE_GROWTH = 2.0

    /** 60 秒的遊戲時間，與核彈相同。 */
    const val COOLDOWN_FRAMES = 3600

    /** 凍住 4 秒。 */
    const val FREEZE_FRAMES = 240

    /** 解凍後再慢 6 秒。 */
    const val CHILL_FRAMES = 360

    /** 解凍後的速度倍率，和一級冰塔差不多。 */
    const val CHILL_FACTOR = 0.5f

    /**
     * 發射一顆：付錢、記次數、進冷卻，再凍住場上每個活著的敵人。
     * 冷卻中、買不起或場上沒有敵人時什麼都不做，回傳 false。特效與音效由呼叫端負責。
     */
    fun tryFire(enemies: List<Enemy>): Boolean {
        val price = price(GameState.freezeUses)
        val targets = enemies.filter { it.isAlive }
        if (GameState.freezeCooldown > 0 || GameState.gold < price || targets.isEmpty()) return false
        GameState.spendGold(price)
        GameState.freezeUses++
        GameState.freezeCooldown = COOLDOWN_FRAMES
        for (enemy in targets) enemy.freeze(FREEZE_FRAMES, CHILL_FACTOR, CHILL_FRAMES)
        return true
    }

    fun price(uses: Int): Int {
        val value = BASE_PRICE * PRICE_GROWTH.pow(uses)
        return if (value >= Int.MAX_VALUE) Int.MAX_VALUE else value.toInt()
    }
}
