package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

class TowerSelectBar(private val screenWidth: Int, private val screenHeight: Int) {

    private val barHeight = 102f
    val barTop: Float get() = screenHeight - barHeight
    private val paint = Paint().apply { isAntiAlias = true }

    data class TowerButton(
        val type: TowerType,
        val emoji: String,
        val cost: Int,
        var rect: RectF = RectF()
    )

    val buttons = listOf(
        TowerButton(TowerType.ARROW, "🏹", 50),
        TowerButton(TowerType.BOMB,  "💣", 80),
        TowerButton(TowerType.ICE,   "❄️", 60)
    )

    init {
        val btnW = minOf(150f, (screenWidth - 72f) / buttons.size)
        val spacing = 14f
        val totalW = buttons.size * btnW + (buttons.size - 1) * spacing
        var startX = (screenWidth - totalW) / 2f
        for (btn in buttons) {
            btn.rect = RectF(startX, barTop + 14f, startX + btnW, barTop + barHeight - 14f)
            startX += btnW + spacing
        }
    }

    fun onTap(x: Float, y: Float): Boolean {
        if (y < barTop) return false
        for (btn in buttons) {
            if (btn.rect.contains(x, y)) {
                TowerManagerHolder.manager?.toggleBuildType(btn.type)
                return true
            }
        }
        return false
    }

    fun draw(canvas: Canvas, selectedType: TowerType?) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, barTop, 0f, screenHeight.toFloat(),
            Color.parseColor("#E3172523"),
            Color.parseColor("#F20E1515"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(RectF(0f, barTop, screenWidth.toFloat(), screenHeight.toFloat()), paint)
        paint.shader = null
        paint.color = Color.parseColor("#335FE36B")
        canvas.drawRect(RectF(0f, barTop, screenWidth.toFloat(), barTop + 3f), paint)

        for (btn in buttons) {
            val isSelected = btn.type == selectedType
            val canAfford = GameState.gold >= btn.cost

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#66000000")
            canvas.drawRoundRect(RectF(btn.rect.left, btn.rect.top + 4f, btn.rect.right, btn.rect.bottom + 4f), 16f, 16f, paint)

            paint.color = when {
                isSelected  -> Color.parseColor("#E7428C4A")
                !canAfford  -> Color.parseColor("#7A33403A")
                else        -> Color.parseColor("#D8293532")
            }
            canvas.drawRoundRect(btn.rect, 16f, 16f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isSelected) 4f else 2f
            paint.color = when {
                isSelected -> Color.parseColor("#D8F8FFB6")
                canAfford -> Color.parseColor("#5CFFFFFF")
                else -> Color.parseColor("#33555555")
            }
            canvas.drawRoundRect(btn.rect, 16f, 16f, paint)

            paint.style = Paint.Style.FILL
            paint.color = if (canAfford) Color.WHITE else Color.parseColor("#77FFFFFF")
            paint.textSize = 35f
            canvas.drawText(btn.emoji, btn.rect.left + 13f, btn.rect.centerY() + 12f, paint)

            paint.textSize = 20f
            paint.isFakeBoldText = true
            paint.color = if (canAfford) Color.parseColor("#FFE08A") else Color.parseColor("#88A0A0A0")
            canvas.drawText("${btn.cost} 🪙", btn.rect.left + 54f, btn.rect.centerY() - 2f, paint)

            paint.textSize = 15f
            paint.isFakeBoldText = false
            paint.color = if (canAfford) Color.parseColor("#CFE7F5D5") else Color.parseColor("#779CA3A0")
            canvas.drawText(towerName(btn.type), btn.rect.left + 54f, btn.rect.centerY() + 22f, paint)
        }
    }

    private fun towerName(type: TowerType): String {
        return when (type) {
            TowerType.ARROW -> "速射"
            TowerType.BOMB -> "範圍"
            TowerType.ICE -> "減速"
        }
    }
}

// 簡單持有 TowerManager 引用，避免循環依賴
object TowerManagerHolder {
    var manager: TowerManager? = null
}
