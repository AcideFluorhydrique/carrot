// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import kotlin.math.ceil

/**
 * 塔、敵人、障礙物上的 emoji「印章」。
 *
 * 每一次 drawText / measureText 都要走一遍文字排版（雙向文字分析、字型回退、字形查找），
 * 後期一個畫面上有上百個 emoji，排版吃掉遊戲執行緒約一成五的時間。
 * 這些 emoji 都是固定字串、大小只有幾種，所以第一次畫的時候把結果錄成 [Picture]，
 * 之後只是重播錄好的字形 —— 畫出來的東西不變，只是省掉重複的排版。
 *
 * 錄製時連同畫筆的顏色與透明度一起錄進去，所以快取的鍵包含這些狀態；
 * 呼叫端畫筆的樣子和原本 drawText 時完全相同，畫面也就相同。
 *
 * 只給固定字串用。傷害數字之類每次都不一樣的文字放進來只會撐大快取。
 * 只在遊戲執行緒上使用，沒有加鎖。
 */
object Glyphs {

    private class Stamp(
        val size: Float,
        val color: Int,
        val bold: Boolean,
        val style: Paint.Style,
        val width: Float,
        val picture: Picture
    )

    /** 同一個字串保留的變化數。螢幕尺寸不會在遊戲中途變，實際上只用得到幾種。 */
    private const val MAX_VARIANTS = 16

    private val stamps = HashMap<String, ArrayList<Stamp>>()

    /** 等同 `paint.measureText(text)`。 */
    fun width(text: String, paint: Paint): Float = stamp(text, paint).width

    /** 等同 `canvas.drawText(text, x, y, paint)`（靠左對齊）。 */
    fun draw(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        val s = stamp(text, paint)
        // 錄製時基線放在 (size, size * 2)，見 [record]。
        canvas.save()
        canvas.translate(x - s.size, y - s.size * 2f)
        canvas.drawPicture(s.picture)
        canvas.restore()
    }

    private fun stamp(text: String, paint: Paint): Stamp {
        val list = stamps.getOrPut(text) { ArrayList(4) }
        val size = paint.textSize
        val color = paint.color
        val bold = paint.isFakeBoldText
        val style = paint.style
        for (i in list.indices) {
            val s = list[i]
            if (s.size == size && s.color == color && s.bold == bold && s.style == style) return s
        }
        val created = record(text, paint)
        if (list.size >= MAX_VARIANTS) list.removeAt(0)
        list.add(created)
        return created
    }

    /**
     * Picture 的錄製畫布會裁到 (0, 0, 寬, 高)，而字形會往基線上方與左右伸出，
     * 所以基線放在離左邊一個字高、離上方兩個字高的地方，四周都留一個字高的空間。
     */
    private fun record(text: String, paint: Paint): Stamp {
        val size = paint.textSize
        val width = paint.measureText(text)
        val picture = Picture()
        val canvas = picture.beginRecording(
            ceil(width + size * 2f).toInt().coerceAtLeast(1),
            ceil(size * 3f).toInt().coerceAtLeast(1)
        )
        canvas.drawText(text, size, size * 2f, paint)
        picture.endRecording()
        return Stamp(size, paint.color, paint.isFakeBoldText, paint.style, width, picture)
    }
}
