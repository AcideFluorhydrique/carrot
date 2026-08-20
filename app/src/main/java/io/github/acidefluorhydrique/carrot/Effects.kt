// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Int,
    val maxLife: Int,
    val color: Int,
    val size: Float,
    val gravity: Float,
    val shrink: Boolean
)

private class FloatText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Int,
    var life: Int,
    val maxLife: Int,
    val size: Float,
    val bold: Boolean
)

private class Ring(
    val x: Float,
    val y: Float,
    val maxRadius: Float,
    var life: Int,
    val maxLife: Int,
    val color: Int,
    val width: Float
)

private class Beam(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    var life: Int,
    val maxLife: Int,
    val color: Int,
    val seed: Int
)

/**
 * 全域特效系統：粒子、飄字、擴散環、閃電光束與螢幕震動。
 * 與 GameState 一樣採單例，方便任何實體直接觸發特效而不用層層傳參。
 */
object Fx {

    private const val MAX_PARTICLES = 420

    private val particles = ArrayList<Particle>()
    private val texts = ArrayList<FloatText>()
    private val rings = ArrayList<Ring>()
    private val beams = ArrayList<Beam>()

    private var shakeFrames = 0
    private var shakeAmp = 0f

    /** 螢幕震動位移，繪製場景前套用（HUD 不套用）。 */
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    private val paint = Paint().apply { isAntiAlias = true }
    private val path = Path()

    fun clear() {
        particles.clear()
        texts.clear()
        rings.clear()
        beams.clear()
        shakeFrames = 0
        shakeAmp = 0f
        offsetX = 0f
        offsetY = 0f
    }

    fun shake(amp: Float, frames: Int) {
        if (amp > shakeAmp) shakeAmp = amp
        if (frames > shakeFrames) shakeFrames = frames
    }

    fun burst(
        x: Float,
        y: Float,
        count: Int,
        color: Int,
        speed: Float,
        size: Float,
        life: Int,
        gravity: Float = 0.15f,
        shrink: Boolean = true
    ) {
        val room = MAX_PARTICLES - particles.size
        if (room <= 0) return
        val n = if (count < room) count else room
        for (i in 0 until n) {
            val angle = Random.nextFloat() * (PI * 2f).toFloat()
            val s = speed * (0.35f + Random.nextFloat() * 0.8f)
            particles.add(
                Particle(
                    x, y,
                    cos(angle) * s, sin(angle) * s,
                    life, life, color,
                    size * (0.6f + Random.nextFloat() * 0.7f),
                    gravity, shrink
                )
            )
        }
    }

    fun ring(x: Float, y: Float, radius: Float, color: Int, life: Int = 20, width: Float = Ui.dp(3f)) {
        rings.add(Ring(x, y, radius, life, life, color, width))
    }

    fun beam(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, life: Int = 10) {
        beams.add(Beam(x1, y1, x2, y2, life, life, color, Random.nextInt(10000)))
    }

    fun text(
        x: Float,
        y: Float,
        value: String,
        color: Int,
        size: Float = Ui.dp(13f),
        life: Int = 46,
        bold: Boolean = true
    ) {
        if (texts.size > 40) return
        texts.add(FloatText(x, y, value, color, life, life, size, bold))
    }

    // ---- 常用組合特效 ----

    fun hitSpark(x: Float, y: Float, color: Int) {
        burst(x, y, 5, color, Ui.dp(1.6f), Ui.dp(2.2f), 14, gravity = 0.05f)
    }

    fun explosion(x: Float, y: Float, radius: Float) {
        ring(x, y, radius, Colors.of("#FFB347"), 18, Ui.dp(4f))
        burst(x, y, 22, Colors.of("#FF8A3D"), Ui.dp(2.6f), Ui.dp(3.4f), 26)
        burst(x, y, 12, Colors.of("#FFE066"), Ui.dp(3.2f), Ui.dp(2.4f), 20)
        burst(x, y, 8, Colors.of("#6B7280"), Ui.dp(1.4f), Ui.dp(4f), 34, gravity = 0.02f)
        shake(Ui.dp(2.5f), 10)
    }

    fun frost(x: Float, y: Float, radius: Float) {
        ring(x, y, radius, Colors.of("#8FD8FF"), 16, Ui.dp(2.5f))
        burst(x, y, 8, Colors.of("#BFEAFF"), Ui.dp(1.6f), Ui.dp(2.2f), 22, gravity = 0.03f)
    }

    fun deathPuff(x: Float, y: Float, color: Int, scale: Float) {
        burst(x, y, (12 * scale).toInt().coerceIn(6, 30), color, Ui.dp(2.2f) * scale, Ui.dp(2.8f) * scale, 26)
        burst(x, y, 6, Colors.of("#FFFFFF"), Ui.dp(1.6f) * scale, Ui.dp(1.8f) * scale, 16, gravity = 0.02f)
    }

    fun goldGain(x: Float, y: Float, amount: Int) {
        text(x, y, "+$amount", Colors.of("#FFD75E"), Ui.dp(12f), 46)
    }

    fun damage(x: Float, y: Float, amount: Int, critical: Boolean = false) {
        val color = if (critical) Colors.of("#FF7A7A") else Colors.of("#FFFFFF")
        text(x, y, amount.toString(), color, Ui.dp(if (critical) 13f else 11f), 32)
    }

    fun update() {
        var i = particles.size - 1
        while (i >= 0) {
            val p = particles[i]
            p.x += p.vx
            p.y += p.vy
            p.vy += p.gravity
            p.vx *= 0.965f
            p.vy *= 0.965f
            p.life--
            if (p.life <= 0) particles.removeAt(i)
            i--
        }

        i = texts.size - 1
        while (i >= 0) {
            val t = texts[i]
            t.y -= Ui.dp(0.45f)
            t.life--
            if (t.life <= 0) texts.removeAt(i)
            i--
        }

        i = rings.size - 1
        while (i >= 0) {
            val r = rings[i]
            r.life--
            if (r.life <= 0) rings.removeAt(i)
            i--
        }

        i = beams.size - 1
        while (i >= 0) {
            val b = beams[i]
            b.life--
            if (b.life <= 0) beams.removeAt(i)
            i--
        }

        if (shakeFrames > 0) {
            shakeFrames--
            val decay = (shakeFrames / 10f).coerceAtMost(1f)
            val amp = shakeAmp * decay
            offsetX = (Random.nextFloat() - 0.5f) * 2f * amp
            offsetY = (Random.nextFloat() - 0.5f) * 2f * amp
            if (shakeFrames == 0) {
                shakeAmp = 0f
                offsetX = 0f
                offsetY = 0f
            }
        }
    }

    fun draw(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for (p in particles) {
            val ratio = p.life.toFloat() / p.maxLife
            paint.color = p.color
            paint.alpha = (255 * ratio).toInt().coerceIn(0, 255)
            val r = if (p.shrink) p.size * ratio else p.size
            canvas.drawCircle(p.x, p.y, r, paint)
        }

        paint.style = Paint.Style.STROKE
        for (r in rings) {
            val progress = 1f - r.life.toFloat() / r.maxLife
            paint.color = r.color
            paint.alpha = (200 * (1f - progress)).toInt().coerceIn(0, 255)
            paint.strokeWidth = r.width * (1f - progress * 0.6f)
            canvas.drawCircle(r.x, r.y, r.maxRadius * (0.25f + progress * 0.85f), paint)
        }

        for (b in beams) {
            val ratio = b.life.toFloat() / b.maxLife
            paint.color = b.color
            paint.alpha = (255 * ratio).toInt().coerceIn(0, 255)
            paint.strokeWidth = Ui.dp(2.4f) * ratio + Ui.dp(0.6f)
            drawJaggedLine(canvas, b)
        }

        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        for (t in texts) {
            val ratio = t.life.toFloat() / t.maxLife
            paint.textSize = t.size
            paint.isFakeBoldText = t.bold
            val width = paint.measureText(t.text)
            paint.color = Color.BLACK
            paint.alpha = (150 * ratio).toInt().coerceIn(0, 255)
            canvas.drawText(t.text, t.x - width / 2f + Ui.dp(0.8f), t.y + Ui.dp(0.8f), paint)
            paint.color = t.color
            paint.alpha = (255 * ratio).toInt().coerceIn(0, 255)
            canvas.drawText(t.text, t.x - width / 2f, t.y, paint)
        }
        paint.isFakeBoldText = false
        paint.alpha = 255
    }

    private fun drawJaggedLine(canvas: Canvas, b: Beam) {
        val dx = b.x2 - b.x1
        val dy = b.y2 - b.y1
        val length = hypot(dx, dy)
        if (length < 1f) return
        val nx = -dy / length
        val ny = dx / length
        val segments = 6
        val rnd = Random(b.seed)
        path.reset()
        path.moveTo(b.x1, b.y1)
        for (s in 1 until segments) {
            val t = s.toFloat() / segments
            val jitter = (rnd.nextFloat() - 0.5f) * Ui.dp(9f)
            path.lineTo(b.x1 + dx * t + nx * jitter, b.y1 + dy * t + ny * jitter)
        }
        path.lineTo(b.x2, b.y2)
        canvas.drawPath(path, paint)
    }
}
