package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.*

class Bullet(
    startX: Float,
    startY: Float,
    private val target: Enemy,
    val damage: Int,
    val type: TowerType,
    val splashRadius: Float = 0f,   // 炸彈塔用
    val slowFactor: Float = 1f,     // 冰塔用（0.5 = 減速50%）
    val slowDuration: Int = 0
) {
    var x = startX
    var y = startY
    var isDone = false
    private val speed = 8f

    private val paint = Paint().apply { isAntiAlias = true }

    fun update(enemies: List<Enemy>) {
        if (isDone) return
        if (target.isDead || target.hasReachedEnd) { isDone = true; return }

        val dx = target.x - x
        val dy = target.y - y
        val dist = sqrt(dx * dx + dy * dy)

        if (dist <= speed) {
            // 命中
            onHit(enemies)
        } else {
            x += dx / dist * speed
            y += dy / dist * speed
        }
    }

    private fun onHit(enemies: List<Enemy>) {
        isDone = true
        when (type) {
            TowerType.ARROW -> target.takeDamage(damage)
            TowerType.BOMB  -> {
                // 範圍傷害
                for (e in enemies) {
                    val dx = e.x - target.x
                    val dy = e.y - target.y
                    if (sqrt(dx * dx + dy * dy) <= splashRadius) {
                        e.takeDamage(damage)
                    }
                }
            }
            TowerType.ICE   -> {
                target.takeDamage(damage)
                target.applySlow(slowFactor, slowDuration)
            }
        }
    }

    fun draw(canvas: Canvas) {
        if (isDone) return
        when (type) {
            TowerType.ARROW -> {
                // 短線子彈，朝目標方向
                val dx = target.x - x
                val dy = target.y - y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val nx = dx / dist * 18f
                val ny = dy / dist * 18f
                paint.strokeWidth = 4f
                paint.color = Color.parseColor("#FFD700")
                canvas.drawLine(x - nx, y - ny, x + nx, y + ny, paint)
            }
            TowerType.BOMB -> {
                paint.textSize = 28f
                canvas.drawText("💥", x - 14f, y + 10f, paint)
            }
            TowerType.ICE -> {
                paint.textSize = 24f
                canvas.drawText("❄️", x - 12f, y + 8f, paint)
            }
        }
    }
}

// Enemy 需要暴露 x, y 給 Bullet，並支持減速
// 下面在 Enemy.kt 增加這些屬性