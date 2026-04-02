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
        // 半透明頂欄背景
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#AA000000")
        canvas.drawRect(RectF(0f, 0f, screenWidth.toFloat(), 70f), paint)

        // 蘿蔔圖標（橙色圓形代替）
        paint.color = Color.parseColor("#FF6600")
        canvas.drawCircle(40f, 35f, 20f, paint)

        // 蘿蔔血量
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.isFakeBoldText = true
        canvas.drawText("× ${GameState.carrotHp}", 70f, 52f, paint)

        // 金幣
        paint.color = Color.parseColor("#FFD700")
        canvas.drawCircle(220f, 35f, 16f, paint)
        paint.color = Color.WHITE
        canvas.drawText("× ${GameState.gold}", 245f, 52f, paint)
    }

    private fun drawDefeat(canvas: Canvas, w: Int, h: Int) {
        // 半透明遮罩
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#BB000000")
        canvas.drawRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)

        paint.color = Color.parseColor("#FF4444")
        paint.textSize = 90f
        paint.isFakeBoldText = true
        val text = "DEFEAT"
        val tw = paint.measureText(text)
        canvas.drawText(text, (w - tw) / 2, h / 2f, paint)

        paint.textSize = 40f
        paint.color = Color.WHITE
        val sub = "蘿蔔被吃掉了！"
        val sw = paint.measureText(sub)
        canvas.drawText(sub, (w - sw) / 2, h / 2f + 60, paint)
    }

    private fun drawVictory(canvas: Canvas, w: Int, h: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#BB003300")
        canvas.drawRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)

        paint.color = Color.parseColor("#44FF44")
        paint.textSize = 90f
        paint.isFakeBoldText = true
        val text = "VICTORY"
        val tw = paint.measureText(text)
        canvas.drawText(text, (w - tw) / 2, h / 2f, paint)

        paint.textSize = 40f
        paint.color = Color.WHITE
        val sub = "蘿蔔保住了！"
        val sw = paint.measureText(sub)
        canvas.drawText(sub, (w - sw) / 2, h / 2f + 60, paint)
    }
}