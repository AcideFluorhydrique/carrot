package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class TowerSelectBar(private val screenWidth: Int, private val screenHeight: Int) {

    private val barHeight = 90f
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
        val btnW = 160f
        val spacing = 20f
        val totalW = buttons.size * btnW + (buttons.size - 1) * spacing
        var startX = (screenWidth - totalW) / 2f
        for (btn in buttons) {
            btn.rect = RectF(startX, barTop + 10f, startX + btnW, barTop + barHeight - 10f)
            startX += btnW + spacing
        }
    }

    fun onTap(x: Float, y: Float): Boolean {
        if (y < barTop) return false
        for (btn in buttons) {
            if (btn.rect.contains(x, y)) {
                TowerManagerHolder.manager?.selectedType =
                    if (TowerManagerHolder.manager?.selectedType == btn.type) null else btn.type
                return true
            }
        }
        return false
    }

    fun draw(canvas: Canvas, selectedType: TowerType?) {
        // 底欄背景
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#AA000000")
        canvas.drawRect(RectF(0f, barTop, screenWidth.toFloat(), screenHeight.toFloat()), paint)

        for (btn in buttons) {
            val isSelected = btn.type == selectedType
            val canAfford = GameState.gold >= btn.cost

            // 按鈕背景
            paint.color = when {
                isSelected  -> Color.parseColor("#AA44AA44")
                !canAfford  -> Color.parseColor("#44555555")
                else        -> Color.parseColor("#AA333333")
            }
            canvas.drawRoundRect(btn.rect, 16f, 16f, paint)

            // 邊框
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = if (isSelected) Color.parseColor("#88FF88") else Color.parseColor("#666666")
            canvas.drawRoundRect(btn.rect, 16f, 16f, paint)

            // emoji
            paint.style = Paint.Style.FILL
            paint.textSize = 34f
            canvas.drawText(btn.emoji, btn.rect.left + 10f, btn.rect.centerY() + 12f, paint)

            // 費用
            paint.textSize = 24f
            paint.color = if (canAfford) Color.parseColor("#FFD700") else Color.parseColor("#AA666666")
            canvas.drawText("${btn.cost}🪙", btn.rect.left + 52f, btn.rect.centerY() + 10f, paint)
        }
    }
}

// 簡單持有 TowerManager 引用，避免循環依賴
object TowerManagerHolder {
    var manager: TowerManager? = null
}