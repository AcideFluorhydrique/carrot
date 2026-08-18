package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * 共用的畫面元件，讓主選單、HUD、面板維持一致的視覺語言。
 * 所有繪製都在遊戲執行緒進行，因此共用一支 Paint 是安全的。
 */
object Widgets {

    const val GREEN_TOP = "#4FA85D"
    const val GREEN_BOTTOM = "#2F7E3C"
    const val BLUE_TOP = "#4A7BA7"
    const val BLUE_BOTTOM = "#2C5479"
    const val AMBER_TOP = "#D7A331"
    const val AMBER_BOTTOM = "#A3701D"
    const val RED_TOP = "#B4553A"
    const val RED_BOTTOM = "#7E3A2A"
    const val GRAY_TOP = "#64748B"
    const val GRAY_BOTTOM = "#44506A"
    const val PURPLE_TOP = "#7C5BA6"
    const val PURPLE_BOTTOM = "#563B79"

    private val paint = Paint().apply { isAntiAlias = true }

    fun panel(
        canvas: Canvas,
        rect: RectF,
        radius: Float = Ui.dp(10f),
        topColor: String = "#F0223029",
        bottomColor: String = "#E9131D1B",
        borderColor: String = "#55FFFFFF"
    ) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.parseColor("#66000000")
        canvas.drawRoundRect(RectF(rect.left, rect.top + Ui.dp(3f), rect.right, rect.bottom + Ui.dp(3f)), radius, radius, paint)
        paint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            Color.parseColor(topColor), Color.parseColor(bottomColor), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = Ui.dp(1.2f)
        paint.color = Color.parseColor(borderColor)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    fun button(
        canvas: Canvas,
        rect: RectF,
        label: String,
        topColor: String = GREEN_TOP,
        bottomColor: String = GREEN_BOTTOM,
        enabled: Boolean = true,
        textSize: Float = Ui.dp(15f),
        subLabel: String? = null
    ) {
        val radius = Ui.dp(9f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#55000000")
        canvas.drawRoundRect(RectF(rect.left, rect.top + Ui.dp(3f), rect.right, rect.bottom + Ui.dp(3f)), radius, radius, paint)

        if (enabled) {
            paint.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                Color.parseColor(topColor), Color.parseColor(bottomColor), Shader.TileMode.CLAMP
            )
        } else {
            paint.color = Color.parseColor("#77404A45")
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = Ui.dp(1.2f)
        paint.color = if (enabled) Color.parseColor("#66FFFFFF") else Color.parseColor("#33FFFFFF")
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.style = Paint.Style.FILL
        paint.color = if (enabled) Color.parseColor("#FFFDF2") else Color.parseColor("#99C9CFC9")
        val inner = rect.width() - Ui.dp(7f)
        if (subLabel == null) {
            val size = fitSize(label, textSize, inner, bold = true)
            centered(canvas, label, rect.centerX(), rect.centerY() + size * 0.36f, size, bold = true)
        } else {
            val mainSize = fitSize(label, textSize, inner, bold = true)
            centered(canvas, label, rect.centerX(), rect.centerY() - textSize * 0.05f, mainSize, bold = true)
            paint.color = if (enabled) Color.parseColor("#CCE9F3E4") else Color.parseColor("#77C9CFC9")
            val subSize = fitSize(subLabel, textSize * 0.7f, inner)
            centered(canvas, subLabel, rect.centerX(), rect.centerY() + textSize * 1.05f, subSize, bold = false)
        }
    }

    /**
     * 把字級縮到剛好塞得下 maxWidth。
     * 翻譯後的長度差異很大（英文普遍比中文長），所有會被翻譯的文字都該走這條。
     */
    fun fitSize(text: String, desired: Float, maxWidth: Float, bold: Boolean = false): Float {
        if (maxWidth <= 0f || text.isEmpty()) return desired
        val width = measure(text, desired, bold)
        if (width <= maxWidth) return desired
        return (desired * (maxWidth / width)).coerceAtLeast(desired * 0.5f)
    }

    fun centeredFit(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baselineY: Float,
        size: Float,
        maxWidth: Float,
        bold: Boolean = false,
        color: Int? = null
    ) {
        centered(canvas, text, centerX, baselineY, fitSize(text, size, maxWidth, bold), bold, color)
    }

    fun leftFit(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        size: Float,
        maxWidth: Float,
        color: Int,
        bold: Boolean = false
    ) {
        left(canvas, text, x, baselineY, fitSize(text, size, maxWidth, bold), color, bold)
    }

    fun centered(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baselineY: Float,
        size: Float,
        bold: Boolean = false,
        color: Int? = null
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.isFakeBoldText = bold
        if (color != null) paint.color = color
        canvas.drawText(text, centerX - paint.measureText(text) / 2f, baselineY, paint)
        paint.isFakeBoldText = false
    }

    fun left(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.isFakeBoldText = bold
        paint.color = color
        canvas.drawText(text, x, baselineY, paint)
        paint.isFakeBoldText = false
    }

    fun measure(text: String, size: Float, bold: Boolean = false): Float {
        paint.textSize = size
        paint.isFakeBoldText = bold
        val w = paint.measureText(text)
        paint.isFakeBoldText = false
        return w
    }

    /** 星等顯示：實心＋空心。 */
    fun stars(canvas: Canvas, centerX: Float, baselineY: Float, earned: Int, size: Float) {
        val total = 3
        val glyph = "★"
        val gap = size * 1.15f
        val startX = centerX - gap * (total - 1) / 2f
        for (i in 0 until total) {
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.textSize = size
            paint.isFakeBoldText = true
            paint.color = if (i < earned) Color.parseColor("#FFD75E") else Color.parseColor("#4DFFFFFF")
            val w = paint.measureText(glyph)
            canvas.drawText(glyph, startX + gap * i - w / 2f, baselineY, paint)
        }
        paint.isFakeBoldText = false
    }

    fun scrim(canvas: Canvas, w: Int, h: Int, color: String) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor(color)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    fun badge(canvas: Canvas, rect: RectF, text: String, background: String, textColor: String, size: Float) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor(background)
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
        centered(canvas, text, rect.centerX(), rect.centerY() + size * 0.36f, size, bold = true, color = Color.parseColor(textColor))
    }
}
