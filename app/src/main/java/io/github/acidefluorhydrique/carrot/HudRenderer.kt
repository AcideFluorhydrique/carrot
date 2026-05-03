package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

class HudRenderer {

    private val paint = Paint().apply { isAntiAlias = true }

    fun draw(canvas: Canvas, screenWidth: Int, screenHeight: Int) {
        drawTopBar(canvas, screenWidth)
        if (GameState.status == GameStatus.DEFEAT) drawDefeat(canvas, screenWidth, screenHeight)
        if (GameState.status == GameStatus.VICTORY) drawVictory(canvas, screenWidth, screenHeight)
    }

    private fun drawTopBar(canvas: Canvas, screenWidth: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, 76f,
            Color.parseColor("#F01A2A2B"),
            Color.parseColor("#D0131D20"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(RectF(0f, 0f, screenWidth.toFloat(), 76f), paint)
        paint.shader = null
        paint.color = Color.parseColor("#335FE36B")
        canvas.drawRect(RectF(0f, 72f, screenWidth.toFloat(), 76f), paint)

        drawResourcePill(canvas, 10f, "🥕", GameState.carrotHp.toString(), "#D96031")
        drawResourcePill(canvas, 142f, "🪙", GameState.gold.toString(), "#D7A331")

        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.isFakeBoldText = false
        val waveText = "${GameState.level.name}  ${GameState.wave}/${GameState.level.waves.size} 波"
        val tw = paint.measureText(waveText)
        val labelLeft = maxOf(286f, screenWidth - tw - 24f)
        paint.color = Color.parseColor("#DDEFE8")
        canvas.drawText(waveText, labelLeft, 45f, paint)
    }

    private fun drawDefeat(canvas: Canvas, w: Int, h: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#D0141014")
        canvas.drawRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)

        paint.textSize = minOf(88f, w / 5.4f)
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#FF6B6B")
        val text = "DEFEAT"
        val tw = paint.measureText(text)
        canvas.drawText(text, (w - tw) / 2, h / 2f, paint)

        paint.textSize = minOf(38f, w / 10f)
        paint.color = Color.WHITE
        val sub = "🥕 蘿蔔被吃掉了！"
        val sw = paint.measureText(sub)
        canvas.drawText(sub, (w - sw) / 2, h / 2f + 60, paint)

        drawBackHint(canvas, w, h)
    }

    private fun drawVictory(canvas: Canvas, w: Int, h: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#D00D2415")
        canvas.drawRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)

        paint.textSize = minOf(88f, w / 5.2f)
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#67E879")
        val text = "VICTORY"
        val tw = paint.measureText(text)
        canvas.drawText(text, (w - tw) / 2, h / 2f, paint)

        paint.textSize = minOf(38f, w / 10f)
        paint.color = Color.WHITE
        val sub = "🥕 蘿蔔保住了！"
        val sw = paint.measureText(sub)
        canvas.drawText(sub, (w - sw) / 2, h / 2f + 60, paint)

        drawBackHint(canvas, w, h)
    }

    private fun drawBackHint(canvas: Canvas, w: Int, h: Int) {
        paint.textSize = 26f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#DDFFFFFF")
        val hint = "點擊任意位置返回主界面"
        val hw = paint.measureText(hint)
        canvas.drawText(hint, (w - hw) / 2, h / 2f + 108f, paint)
    }

    private fun drawResourcePill(canvas: Canvas, left: Float, icon: String, value: String, accent: String) {
        val rect = RectF(left, 12f, left + 118f, 60f)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#66000000")
        canvas.drawRoundRect(RectF(rect.left, rect.top + 3f, rect.right, rect.bottom + 3f), 18f, 18f, paint)
        paint.color = Color.parseColor("#E6263430")
        canvas.drawRoundRect(rect, 18f, 18f, paint)

        paint.color = Color.parseColor(accent)
        canvas.drawCircle(rect.left + 24f, rect.centerY(), 17f, paint)

        paint.textSize = 28f
        canvas.drawText(icon, rect.left + 10f, rect.centerY() + 10f, paint)

        paint.color = Color.parseColor("#FFFDF2")
        paint.textSize = 25f
        paint.isFakeBoldText = true
        canvas.drawText("× $value", rect.left + 50f, rect.centerY() + 9f, paint)
        paint.isFakeBoldText = false
    }
}
