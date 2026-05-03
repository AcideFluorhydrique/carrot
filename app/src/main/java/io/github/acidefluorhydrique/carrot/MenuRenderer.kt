package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

class MenuRenderer {

    private val paint = Paint().apply { isAntiAlias = true }
    private val startButton = RectF()
    private val levelButton = RectF()
    private val backButton = RectF()
    private val levelButtons = mutableListOf<Pair<LevelConfig, RectF>>()

    fun drawMain(canvas: Canvas, w: Int, h: Int) {
        drawBackground(canvas, w, h)

        paint.shader = null
        paint.color = Color.parseColor("#FFF7D6")
        paint.isFakeBoldText = true
        paint.textSize = responsiveText(w, 62f, 44f)
        drawCentered(canvas, "保衛蘿蔔田", w / 2f, h * 0.22f)

        paint.isFakeBoldText = false
        paint.textSize = responsiveText(w, 25f, 19f)
        paint.color = Color.parseColor("#DCEAD6")
        drawCentered(canvas, "放塔、升級、守住最後一根胡蘿蔔", w / 2f, h * 0.22f + 46f)

        val btnW = minOf(360f, w - 48f)
        val btnH = 68f
        startButton.set((w - btnW) / 2f, h * 0.47f, (w + btnW) / 2f, h * 0.47f + btnH)
        levelButton.set(startButton.left, startButton.bottom + 20f, startButton.right, startButton.bottom + 20f + btnH)
        drawButton(canvas, startButton, "開始遊戲", "#4FA85D", "#2F7E3C")
        drawButton(canvas, levelButton, "選擇關卡", "#426D93", "#294D6F")

        paint.textSize = 22f
        paint.color = Color.parseColor("#BBFFFFFF")
        drawCentered(canvas, "目前關卡：${GameState.level.name}", w / 2f, levelButton.bottom + 42f)
    }

    fun drawLevels(canvas: Canvas, w: Int, h: Int) {
        drawBackground(canvas, w, h)
        levelButtons.clear()

        paint.shader = null
        paint.color = Color.parseColor("#FFF7D6")
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
            paint.color = Color.parseColor("#33000000")
            canvas.drawRoundRect(RectF(rect.left, rect.top + 5f, rect.right, rect.bottom + 5f), 14f, 14f, paint)
            paint.color = if (selected) Color.parseColor("#E8508D5B") else Color.parseColor("#D91E3442")
            canvas.drawRoundRect(rect, 14f, 14f, paint)

            paint.color = if (selected) Color.parseColor("#334FEA6A") else Color.parseColor("#22FFFFFF")
            canvas.drawRoundRect(RectF(rect.left + 2f, rect.top + 2f, rect.right - 2f, rect.top + 34f), 12f, 12f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (selected) Color.parseColor("#D6EFFFBA") else Color.parseColor("#5CFFFFFF")
            canvas.drawRoundRect(rect, 14f, 14f, paint)

            paint.style = Paint.Style.FILL
            paint.isFakeBoldText = true
            paint.textSize = 28f
            paint.color = Color.parseColor("#FFFDF2")
            canvas.drawText("${level.id}. ${level.name}", rect.left + 18f, rect.top + 38f, paint)

            paint.isFakeBoldText = false
            paint.textSize = 21f
            paint.color = Color.parseColor("#DDFFFFFF")
            canvas.drawText(level.subtitle, rect.left + 18f, rect.top + 72f, paint)
            canvas.drawText("${level.waves.size} 波", rect.right - 78f, rect.top + 72f, paint)
            top += cardH + 16f
        }

        backButton.set(24f, h - 82f, 154f, h - 24f)
        drawButton(canvas, backButton, "返回", "#64748B", "#475569")
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
        val sky = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.parseColor("#172E34"),
                Color.parseColor("#1F3B2B"),
                Color.parseColor("#40672F")
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        paint.shader = sky
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        paint.color = Color.parseColor("#2B5531")
        canvas.drawRoundRect(RectF(-24f, h * 0.61f, w + 24f, h + 28f), 36f, 36f, paint)
        paint.color = Color.parseColor("#8D6A2E")
        for (i in 0 until 8) {
            val y = h * 0.66f + i * 36f
            canvas.drawRoundRect(RectF(24f, y, w - 24f, y + 7f), 6f, 6f, paint)
        }
        paint.color = Color.parseColor("#44FFFFFF")
        canvas.drawCircle(w * 0.82f, h * 0.16f, 42f, paint)
        paint.color = Color.parseColor("#2277D95C")
        canvas.drawCircle(w * 0.14f, h * 0.38f, 72f, paint)
        paint.textSize = 76f
        paint.color = Color.WHITE
        canvas.drawText("🥕", w * 0.15f, h * 0.43f, paint)
        paint.textSize = 58f
        canvas.drawText("🥕", w * 0.76f, h * 0.56f, paint)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, topColor: String, bottomColor: String) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.parseColor("#44000000")
        canvas.drawRoundRect(RectF(rect.left, rect.top + 4f, rect.right, rect.bottom + 4f), 14f, 14f, paint)
        paint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            Color.parseColor(topColor),
            Color.parseColor(bottomColor),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, 14f, 14f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#55FFFFFF")
        canvas.drawRoundRect(rect, 14f, 14f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FFFDF2")
        paint.isFakeBoldText = true
        paint.textSize = 28f
        drawCentered(canvas, label, rect.centerX(), rect.centerY() + 10f)
        paint.isFakeBoldText = false
    }

    private fun drawCentered(canvas: Canvas, text: String, x: Float, y: Float) {
        val tw = paint.measureText(text)
        canvas.drawText(text, x - tw / 2f, y, paint)
    }

    private fun responsiveText(w: Int, target: Float, min: Float): Float {
        return minOf(target, maxOf(min, w / 9.2f))
    }
}

enum class MenuAction { NONE, START, LEVELS }
