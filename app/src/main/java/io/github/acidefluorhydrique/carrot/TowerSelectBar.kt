package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
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
        for (i in TYPES.indices) {
            if (buttonRect(w, h, i).contains(x, y)) {
                towerManager.toggleBuildType(TYPES[i])
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
            Color.parseColor("#E6172523"), Color.parseColor("#F40C1312"), Shader.TileMode.CLAMP
        )
        canvas.drawRect(RectF(0f, top, w.toFloat(), h.toFloat()), paint)
        paint.shader = null
        paint.color = Color.parseColor("#445FE36B")
        canvas.drawRect(RectF(0f, top, w.toFloat(), top + Ui.dp(1.5f)), paint)

        for (i in TYPES.indices) {
            drawButton(canvas, buttonRect(w, h, i), TYPES[i], TYPES[i] == selectedType)
        }
    }

    private fun drawButton(canvas: Canvas, rect: RectF, type: TowerType, selected: Boolean) {
        val affordable = GameState.gold >= type.baseCost
        val radius = Ui.dp(9f)

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.parseColor("#66000000")
        canvas.drawRoundRect(RectF(rect.left, rect.top + Ui.dp(2.5f), rect.right, rect.bottom + Ui.dp(2.5f)), radius, radius, paint)

        paint.color = when {
            selected -> Color.parseColor("#E63F8A4C")
            affordable -> Color.parseColor("#D8242F2C")
            else -> Color.parseColor("#8A2A2F2C")
        }
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) Ui.dp(2f) else Ui.dp(1.1f)
        paint.color = when {
            selected -> Color.parseColor("#E0F8FFB6")
            affordable -> Color.parseColor("#5AFFFFFF")
            else -> Color.parseColor("#33FFFFFF")
        }
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.textSize = rect.height() * 0.36f
        val emoji = type.emoji
        canvas.drawText(emoji, rect.centerX() - paint.measureText(emoji) / 2f, rect.top + rect.height() * 0.42f, paint)

        val inner = rect.width() - Ui.dp(5f)
        Widgets.centeredFit(
            canvas, type.displayName, rect.centerX(), rect.top + rect.height() * 0.68f,
            rect.height() * 0.2f, inner, bold = false,
            color = if (affordable) Color.parseColor("#DCEDE2") else Color.parseColor("#7F97A0")
        )
        Widgets.centeredFit(
            canvas, Strings.format(R.string.tower_price, type.baseCost), rect.centerX(),
            rect.top + rect.height() * 0.92f, rect.height() * 0.21f, inner, bold = true,
            color = if (affordable) Color.parseColor("#FFE08A") else Color.parseColor("#8A96A0")
        )
    }

    companion object {
        val TYPES: List<TowerType> = TowerType.values().toList()

        fun barTop(h: Int): Float = h - Ui.bottomBarHeight

        fun buttonRect(w: Int, h: Int, index: Int): RectF {
            val count = TYPES.size
            val gap = Ui.dp(7f)
            val available = w - Ui.dp(18f) - gap * (count - 1)
            val width = (available / count).coerceAtMost(Ui.dp(84f))
            val height = Ui.bottomBarHeight - Ui.dp(12f)
            val total = width * count + gap * (count - 1)
            val left = (w - total) / 2f + index * (width + gap)
            val top = barTop(h) + Ui.dp(6f)
            return RectF(left, top, left + width, top + height)
        }
    }
}
