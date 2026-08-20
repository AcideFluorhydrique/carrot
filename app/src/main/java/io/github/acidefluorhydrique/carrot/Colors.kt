// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Color
import java.util.concurrent.ConcurrentHashMap

/**
 * 顏色字串的解析快取。
 *
 * 畫面全部是 Canvas 自繪，顏色寫成十六進位字串散在各個 renderer 裡。
 * 直接呼叫 [Color.parseColor] 的問題不在 CPU，而在配置：它內部要 substring
 * 出一個新字串再 parseLong，而這些呼叫全都躺在每幀的 draw 路徑上 ——
 * 光是選塔列一輪就有近百次，等於每秒丟幾千個短命字串給 GC，
 * 在低階機上會表現成不規律的掉幀。
 *
 * 這裡用字串本身當鍵快取解析結果。顏色字面量在 Kotlin 裡是 interned 的，
 * hashCode 算過一次就快取住，所以查表這條路上不會再產生任何配置。
 *
 * 用 ConcurrentHashMap 而不是 HashMap：目前繪製與觸控共用 GameThread 的那把鎖，
 * 但那是呼叫端的巧合，不該讓一個全域快取依賴它。
 */
object Colors {

    private val cache = ConcurrentHashMap<String, Int>()

    fun of(hex: String): Int = cache.computeIfAbsent(hex) { Color.parseColor(it) }
}
