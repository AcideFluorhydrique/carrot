// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * 底部選塔列。所有矩形都由畫面尺寸即時算出，
 * 因此觸控（UI 執行緒）與繪製（遊戲執行緒）不會共享可變狀態。
 */
class TowerSelectBar {

    private val paint = Paint().apply { isAntiAlias = true }

    /** 回傳 true 表示這次點擊落在選塔列上。 */
    fun onTap(x: Float, y: Float, w: Int, h: Int, towerManager: TowerManager): Boolean {
        if (y < barTop(h)) return false
        val types = types()
        for (i in types.indices) {
            if (buttonRect(w, h, i, types.size).contains(x, y)) {
                if (types[i].isAvailable) {
                    towerManager.toggleBuildType(types[i])
                } else {
                    Audio.play(Sfx.DENY)
                }
                return true
            }
        }
        return true   // 吃掉落在列上的空白點擊，避免誤蓋塔
    }

    fun draw(canvas: Canvas, w: Int, h: Int, selectedType: TowerType?) {
        val top = barTop(h)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, top, 0f, h.toFloat(),
            Colors.of("#E6172523"), Colors.of("#F40C1312"), Shader.TileMode.CLAMP
        )
        canvas.drawRect(RectF(0f, top, w.toFloat(), h.toFloat()), paint)
        paint.shader = null
        paint.color = Colors.of("#445FE36B")
        canvas.drawRect(RectF(0f, top, w.toFloat(), top + Ui.dp(1.5f)), paint)

        val types = types()
        for (i in types.indices) {
            drawButton(canvas, buttonRect(w, h, i, types.size), types[i], types[i] == selectedType)
        }
    }

    private fun drawButton(canvas: Canvas, rect: RectF, type: TowerType, selected: Boolean) {
        val unlocked = type.isUnlocked
        val affordable = unlocked && GameState.gold >= type.baseCost
        val radius = Ui.dp(9f)

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Colors.of("#66000000")
        canvas.drawRoundRect(RectF(rect.left, rect.top + Ui.dp(2.5f), rect.right, rect.bottom + Ui.dp(2.5f)), radius, radius, paint)

        paint.color = when {
            selected -> Colors.of("#E63F8A4C")
            affordable -> Colors.of("#D8242F2C")
            else -> Colors.of("#8A2A2F2C")
        }
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) Ui.dp(2f) else Ui.dp(1.1f)
        paint.color = when {
            selected -> Colors.of("#E0F8FFB6")
            affordable -> Colors.of("#5AFFFFFF")
            else -> Colors.of("#33FFFFFF")
        }
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.style = Paint.Style.FILL
        paint.alpha = if (unlocked) 255 else 110
        paint.textSize = rect.height() * 0.36f
        val emoji = if (unlocked) type.emoji else "🔒"
        canvas.drawText(emoji, rect.centerX() - paint.measureText(emoji) / 2f, rect.top + rect.height() * 0.42f, paint)
        paint.alpha = 255

        val inner = rect.width() - Ui.dp(5f)
        Widgets.centeredFit(
            canvas, type.displayName, rect.centerX(), rect.top + rect.height() * 0.68f,
            rect.height() * 0.2f, inner, bold = false,
            color = if (affordable) Colors.of("#DCEDE2") else Colors.of("#7F97A0")
        )
        // 未解鎖時，第三行改成提示要打到第幾關
        val bottom = if (unlocked) {
            Strings.format(R.string.tower_price, type.baseCost)
        } else {
            type.unlockHint
        }
        Widgets.centeredFit(
            canvas, bottom, rect.centerX(),
            rect.top + rect.height() * 0.92f, rect.height() * 0.21f, inner, bold = true,
            color = when {
                !unlocked -> Colors.of("#94A3B8")
                affordable -> Colors.of("#FFE08A")
                else -> Colors.of("#8A96A0")
            }
        )
    }

    companion object {
        /** 每關只給一部分的塔，讓同一批工具能組出不同的題目。 */
        fun types(): List<TowerType> = GameState.level.allowedTowers

        fun barTop(h: Int): Float = h - Ui.bottomBarHeight

        fun buttonRect(w: Int, h: Int, index: Int, count: Int): RectF {
            val gap = Ui.dp(7f)
            val available = w - Ui.dp(18f) - gap * (count - 1)
            val width = (available / count.coerceAtLeast(1)).coerceAtMost(Ui.dp(92f))
            val height = Ui.bottomBarHeight - Ui.dp(12f)
            val total = width * count + gap * (count - 1)
            val left = (w - total) / 2f + index * (width + gap)
            val top = barTop(h) + Ui.dp(6f)
            return RectF(left, top, left + width, top + height)
        }
    }
}
