package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.*

class Enemy(
    private val gameMap: GameMap,
    wave: WaveConfig
) {

    private var pathIndex = 0
    var distanceTravelled = 0f
        private set
    var x = 0f                  // ← 改為 var 暴露給 Bullet
        private set
    var y = 0f
        private set

    var hp: Int = wave.hp
    var maxHp: Int = wave.hp
    var baseSpeed: Float = wave.speed
    var isDead = false
    var hasReachedEnd = false
    var goldReward: Int = wave.reward

    // 減速狀態
    private var slowFactor: Float = 1f
    private var slowTimer: Int = 0

    val speed: Float get() = baseSpeed * slowFactor

    init {
        val (col, row) = gameMap.pathPoints[0]
        val (px, py) = gameMap.cellToPixel(col, row)
        x = px + gameMap.cellSize / 2
        y = py + gameMap.cellSize / 2
    }

    fun applySlow(factor: Float, duration: Int) {
        slowFactor = factor
        slowTimer = duration
    }

    fun update() {
        if (isDead || hasReachedEnd) return

        // 減速計時
        if (slowTimer > 0) {
            slowTimer--
            if (slowTimer == 0) slowFactor = 1f
        }

        if (pathIndex >= gameMap.pathPoints.size - 1) {
            hasReachedEnd = true
            GameState.onEnemyReached()
            return
        }

        val (targetCol, targetRow) = gameMap.pathPoints[pathIndex + 1]
        val (targetPx, targetPy) = gameMap.cellToPixel(targetCol, targetRow)
        val targetX = targetPx + gameMap.cellSize / 2
        val targetY = targetPy + gameMap.cellSize / 2

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)

        if (dist <= speed) {
            x = targetX
            y = targetY
            pathIndex++
            distanceTravelled += dist
        } else {
            val stepX = dx / dist * speed
            val stepY = dy / dist * speed
            x += stepX
            y += stepY
            distanceTravelled += speed
        }
    }

    fun takeDamage(dmg: Int) {
        hp -= dmg
        if (hp <= 0) {
            isDead = true
            GameState.gold += goldReward
        }
    }

    private val paint = Paint().apply { isAntiAlias = true }

    fun draw(canvas: Canvas) {
        if (isDead || hasReachedEnd) return

        val r = gameMap.cellSize * 0.3f

        // 減速時顯示藍色光暈
        if (slowFactor < 1f) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#6644AAFF")
            canvas.drawCircle(x, y, r * 1.3f, paint)
        }

        paint.textSize = r * 2f
        canvas.drawText("👾", x - r, y + r * 0.7f, paint)

        // 血條
        val barW = r * 2.2f
        val barH = 6f
        val barLeft = x - barW / 2
        val barTop = y - r - 12f
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#555555")
        canvas.drawRect(RectF(barLeft, barTop, barLeft + barW, barTop + barH), paint)
        paint.color = if (slowFactor < 1f) Color.parseColor("#44AAFF") else Color.parseColor("#44FF44")
        canvas.drawRect(
            RectF(barLeft, barTop, barLeft + barW * (hp.toFloat() / maxHp), barTop + barH),
            paint
        )
    }
}
