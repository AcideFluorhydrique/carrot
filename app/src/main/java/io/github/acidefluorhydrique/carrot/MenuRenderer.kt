package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class MenuRenderer {

    private val paint = Paint().apply { isAntiAlias = true }
    private val startButton = RectF()
    private val levelButton = RectF()
    private val backButton = RectF()
    private val levelButtons = mutableListOf<Pair<LevelConfig, RectF>>()

    fun drawMain(canvas: Canvas, w: Int, h: Int) {
        drawBackground(canvas, w, h)

        paint.color = Color.WHITE
        paint.isFakeBoldText = true
        paint.textSize = 58f
        drawCentered(canvas, "保衛蘿蔔田", w / 2f, h * 0.24f)

        paint.isFakeBoldText = false
        paint.textSize = 25f
        paint.color = Color.parseColor("#DDEFE8")
        drawCentered(canvas, "放塔、升級、守住最後一根胡蘿蔔", w / 2f, h * 0.24f + 48f)

        val btnW = minOf(360f, w - 48f)
        val btnH = 68f
        startButton.set((w - btnW) / 2f, h * 0.48f, (w + btnW) / 2f, h * 0.48f + btnH)
        levelButton.set(startButton.left, startButton.bottom + 20f, startButton.right, startButton.bottom + 20f + btnH)
        drawButton(canvas, startButton, "開始遊戲", "#2E7D32")
        drawButton(canvas, levelButton, "選擇關卡", "#355C7D")

        paint.textSize = 22f
        paint.color = Color.parseColor("#BBFFFFFF")
        drawCentered(canvas, "目前關卡：${GameState.level.name}", w / 2f, levelButton.bottom + 42f)
    }

    fun drawLevels(canvas: Canvas, w: Int, h: Int) {
        drawBackground(canvas, w, h)
        levelButtons.clear()

        paint.color = Color.WHITE
        paint.isFakeBoldText = true
        paint.textSize = 46f
        drawCentered(canvas, "選擇關卡", w / 2f, 94f)

        val cardW = minOf(520f, w - 40f)
        val cardH = 104f
        var top = 146f
        for (level in GameLevels.all) {
            val rect = RectF((w - cardW) / 2f, top, (w + cardW) / 2f, top + cardH)
            levelButtons.add(level to rect)
            val selected = level.id == GameState.level.id
            paint.style = Paint.Style.FILL
            paint.color = if (selected) Color.parseColor("#CC2E7D32") else Color.parseColor("#AA1E293B")
            canvas.drawRoundRect(rect, 10f, 10f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (selected) Color.parseColor("#AAE8FFAA") else Color.parseColor("#55FFFFFF")
            canvas.drawRoundRect(rect, 10f, 10f, paint)

            paint.style = Paint.Style.FILL
            paint.isFakeBoldText = true
            paint.textSize = 28f
            paint.color = Color.WHITE
            canvas.drawText("${level.id}. ${level.name}", rect.left + 18f, rect.top + 38f, paint)

            paint.isFakeBoldText = false
            paint.textSize = 21f
            paint.color = Color.parseColor("#DDFFFFFF")
            canvas.drawText(level.subtitle, rect.left + 18f, rect.top + 72f, paint)
            canvas.drawText("${level.waves.size} 波", rect.right - 78f, rect.top + 72f, paint)
            top += cardH + 16f
        }

        backButton.set(24f, h - 82f, 154f, h - 24f)
        drawButton(canvas, backButton, "返回", "#4B5563")
    }

    fun mainTap(x: Float, y: Float): MenuAction {
        return when {
            startButton.contains(x, y) -> MenuAction.START
            levelButton.contains(x, y) -> MenuAction.LEVELS
            else -> MenuAction.NONE
        }
    }

    fun levelTap(x: Float, y: Float): LevelConfig? {
        if (backButton.contains(x, y)) return null
        return levelButtons.firstOrNull { it.second.contains(x, y) }?.first
    }

    fun tappedBack(x: Float, y: Float): Boolean = backButton.contains(x, y)

    private fun drawBackground(canvas: Canvas, w: Int, h: Int) {
        canvas.drawColor(Color.parseColor("#17251B"))
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#234A2A")
        canvas.drawRect(RectF(0f, h * 0.62f, w.toFloat(), h.toFloat()), paint)
        paint.color = Color.parseColor("#7B5E24")
        for (i in 0 until 8) {
            val y = h * 0.66f + i * 36f
            canvas.drawRect(RectF(0f, y, w.toFloat(), y + 8f), paint)
        }
        paint.textSize = 70f
        canvas.drawText("🥕", w * 0.16f, h * 0.42f, paint)
        canvas.drawText("🥕", w * 0.76f, h * 0.53f, paint)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, color: String) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor(color)
        canvas.drawRoundRect(rect, 10f, 10f, paint)
        paint.color = Color.WHITE
        paint.isFakeBoldText = true
        paint.textSize = 28f
        drawCentered(canvas, label, rect.centerX(), rect.centerY() + 10f)
        paint.isFakeBoldText = false
    }

    private fun drawCentered(canvas: Canvas, text: String, x: Float, y: Float) {
        val tw = paint.measureText(text)
        canvas.drawText(text, x - tw / 2f, y, paint)
    }
}

enum class MenuAction { NONE, START, LEVELS }
