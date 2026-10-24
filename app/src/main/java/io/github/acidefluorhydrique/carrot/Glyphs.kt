// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import kotlin.math.ceil
import kotlin.math.round

/**
 * 文字的「印章」：塔、敵人、障礙物上的 emoji，以及 HUD、按鈕上的字。
 *
 * 每一次 drawText / measureText 都要走一遍文字排版（雙向文字分析、字型回退、字形查找），
 * 一個畫面上有上百段文字，排版吃掉遊戲執行緒相當大的一塊。
 * 但這些文字幀與幀之間幾乎不變，所以第一次畫的時候把結果錄成 [Picture]，
 * 之後只是重播錄好的字形 —— 畫出來的東西不變，只是省掉重複的排版。
 *
 * 錄製時連同畫筆的顏色與透明度一起錄進去，所以快取的鍵包含這些狀態；
 * 呼叫端畫筆的樣子和原本 drawText 時完全相同，畫面也就相同。
 *
 * 金幣、分數這類會變的文字也走這裡：舊的字串一段時間沒畫就會被擠出去（[MAX_TEXTS]），
 * 變動時多付的只是錄一次的成本，和原本直接畫差不多。
 * 每次都不同、只活幾十幀的傷害數字則不該放進來。
 *
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

    /** 同一個字串保留的變化數（字級、顏色）。 */
    private const val MAX_VARIANTS = 16

    /** 保留的字串數。HUD 加選塔列一幀大約五十段，留足餘裕給選單。 */
    private const val MAX_TEXTS = 256

    /** 依最近使用排序，超過上限時丟掉最久沒畫的字串。 */
    private val stamps = object : LinkedHashMap<String, ArrayList<Stamp>>(MAX_TEXTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayList<Stamp>>): Boolean =
            size > MAX_TEXTS
    }

    /** 等同 `paint.measureText(text)`。寬度只看字級與粗體，不必為了量寬度另外錄一份。 */
    fun width(text: String, paint: Paint): Float {
        val list = stamps[text]
        if (list != null) {
            val size = paint.textSize
            val bold = paint.isFakeBoldText
            for (i in list.indices) {
                val s = list[i]
                if (s.size == size && s.bold == bold) return s.width
            }
        }
        return stamp(text, paint).width
    }

    /**
     * 等同 `canvas.drawText(text, x, y, paint)`（靠左對齊、無 shader）。
     *
     * 位置會對齊到整數像素。GPU 的文字快取以「錄好的字形 + 位置的小數部分」為鍵：
     * 同一份字形在幾十個不同的小數位置重播，就會存成幾十份，每次查找都要逐一比對，
     * 實測反而讓 RenderThread 比直接 drawText 還忙。小數部分固定為 0 時永遠命中同一份。
     * 字形本來就會落在整數像素上，對齊只讓位置差不到半個像素。
     * 外層的平移也必須是整數，見 GameView 的震動位移。
     */
    fun draw(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        val s = stamp(text, paint)
        // 錄製時基線放在 (size, size * 2)，見 [record]。
        canvas.save()
        canvas.translate(round(x - s.size), round(y - s.size * 2f))
        canvas.drawPicture(s.picture)
        canvas.restore()
    }

    private fun stamp(text: String, paint: Paint): Stamp {
        var list = stamps[text]
        if (list == null) {
            list = ArrayList(4)
            stamps[text] = list
        }
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
