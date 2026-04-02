package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class HudRenderer {

    private val paint = Paint().apply { isAntiAlias = true }

    fun draw(canvas: Canvas, screenWidth: Int, screenHeight: Int) {
        drawTopBar(canvas, screenWidth)
        if (GameState.status == GameStatus.DEFEAT) drawDefeat(canvas, screenWidth, screenHeight)
        if (GameState.status == GameStatus.VICTORY) drawVictory(canvas, screenWidth, screenHeight)
    }

    private fun drawTopBar(canvas: Canvas, screenWidth: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#AA000000")
        canvas.drawRect(RectF(0f, 0f, screenWidth.toFloat(), 70f), paint)

        // ← 🥕 emoji 替換橙色圓形
        paint.textSize = 40f
        canvas.drawText("🥕", 10f, 52f, paint)

        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.isFakeBoldText = true
        canvas.drawText("× ${GameState.carrotHp}", 60f, 52f, paint)

        // ← 金幣也用 emoji
        paint.textSize = 36f
        canvas.drawText("🪙", 190f, 52f, paint)
        paint.color = Color.WHITE
        canvas.drawText("× ${GameState.gold}", 235f, 52f, paint)
    }

    private fun drawDefeat(canvas: Canvas, w: Int, h: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#BB000000")
        canvas.drawRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)

        paint.textSize = 90f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#FF4444")
        val text = "DEFEAT"
        val tw = paint.measureText(text)
        canvas.drawText(text, (w - tw) / 2, h / 2f, paint)

        paint.textSize = 40f
        paint.color = Color.WHITE
        val sub = "🥕 蘿蔔被吃掉了！"
        val sw = paint.measureText(sub)
        canvas.drawText(sub, (w - sw) / 2, h / 2f + 60, paint)
    }

    private fun drawVictory(canvas: Canvas, w: Int, h: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#BB003300")
        canvas.drawRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)

        paint.textSize = 90f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#44FF44")
        val text = "VICTORY"
        val tw = paint.measureText(text)
        canvas.drawText(text, (w - tw) / 2, h / 2f, paint)

        paint.textSize = 40f
        paint.color = Color.WHITE
        val sub = "🥕 蘿蔔保住了！"
        val sw = paint.measureText(sub)
        canvas.drawText(sub, (w - sw) / 2, h / 2f + 60, paint)
    }
}