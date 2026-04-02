package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class Enemy(private val gameMap: GameMap) {

    // 當前在路徑上的進度：pathIndex = 正走向第幾個路徑點
    private var pathIndex = 0
    private var x = 0f
    private var y = 0f

    var hp: Int = 3
    var maxHp: Int = 3
    var speed: Float = 2f        // 每幀移動像素數
    var isDead = false
    var hasReachedEnd = false

    init {
        // 初始位置 = 路徑第一個點的像素中心
        val (col, row) = gameMap.pathPoints[0]
        val (px, py) = gameMap.cellToPixel(col, row)
        x = px + gameMap.cellSize / 2
        y = py + gameMap.cellSize / 2
    }

    fun update() {
        if (isDead || hasReachedEnd) return
        if (pathIndex >= gameMap.pathPoints.size - 1) {
            // 抵達終點
            hasReachedEnd = true
            GameState.onEnemyReached()
            return
        }

        // 目標點
        val (targetCol, targetRow) = gameMap.pathPoints[pathIndex + 1]
        val (targetPx, targetPy) = gameMap.cellToPixel(targetCol, targetRow)
        val targetX = targetPx + gameMap.cellSize / 2
        val targetY = targetPy + gameMap.cellSize / 2

        val dx = targetX - x
        val dy = targetY - y
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (dist <= speed) {
            // 到達這個路徑點，前進到下一個
            x = targetX
            y = targetY
            pathIndex++
        } else {
            x += dx / dist * speed
            y += dy / dist * speed
        }
    }

    fun takeDamage(dmg: Int) {
        hp -= dmg
        if (hp <= 0) isDead = true
    }

    private val paint = Paint().apply { isAntiAlias = true }

    fun draw(canvas: Canvas) {
        if (isDead || hasReachedEnd) return

        val r = gameMap.cellSize * 0.3f

        // 身體
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#CC3333")
        canvas.drawCircle(x, y, r, paint)

        // 血條背景
        val barW = r * 2.2f
        val barH = 8f
        val barLeft = x - barW / 2
        val barTop = y - r - 14f
        paint.color = Color.parseColor("#555555")
        canvas.drawRect(RectF(barLeft, barTop, barLeft + barW, barTop + barH), paint)

        // 血條
        paint.color = Color.parseColor("#44FF44")
        canvas.drawRect(
            RectF(barLeft, barTop, barLeft + barW * (hp.toFloat() / maxHp), barTop + barH),
            paint
        )
    }
}