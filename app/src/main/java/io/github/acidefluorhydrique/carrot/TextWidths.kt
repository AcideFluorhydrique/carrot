// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Paint

/**
 * measureText 的結果快取。
 *
 * 置中的文字每一幀都要先量寬度再畫，而量寬度要走一遍完整的文字排版
 * （雙向文字分析、字型回退、字形查找）。塔、敵人的 emoji 與 HUD 的字幀與幀之間幾乎不變，
 * 寬度只取決於字串、字級與粗體，記住就好。
 *
 * 只快取寬度，不快取繪製：試過把文字錄成 Picture 重播，同一段字形一幀重播上百次，
 * Skia 的 GPU 文字快取反而讓 RenderThread 忙到八成以上，所以 drawText 照舊。
 *
 * 金幣、分數這類會變的字串也走這裡，最久沒用的字串會被擠出去（[MAX_TEXTS]）。
 * 只在遊戲執行緒上使用（觸控與繪製共用同一把鎖），沒有另外加鎖。
 */
object TextWidths {

    private class Width(val size: Float, val bold: Boolean, val value: Float)

    /** 同一個字串保留的字級變化數。 */
    private const val MAX_VARIANTS = 8

    /** 保留的字串數。HUD 加選塔列一幀大約五十段，留足餘裕給選單。 */
    private const val MAX_TEXTS = 256

    /** 依最近使用排序，超過上限時丟掉最久沒用的字串。 */
    private val widths = object : LinkedHashMap<String, ArrayList<Width>>(MAX_TEXTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayList<Width>>): Boolean =
            size > MAX_TEXTS
    }

    /** 等同 `paint.measureText(text)`，寬度只看字級與粗體。 */
    fun of(text: String, paint: Paint): Float {
        val size = paint.textSize
        val bold = paint.isFakeBoldText
        var list = widths[text]
        if (list != null) {
            for (i in list.indices) {
                val w = list[i]
                if (w.size == size && w.bold == bold) return w.value
            }
        } else {
            list = ArrayList(2)
            widths[text] = list
        }
        val value = paint.measureText(text)
        if (list.size >= MAX_VARIANTS) list.removeAt(0)
        list.add(Width(size, bold, value))
        return value
    }
}
