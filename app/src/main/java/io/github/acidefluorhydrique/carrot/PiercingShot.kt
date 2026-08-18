// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * 火箭：沿著固定方向直飛，路徑上的敵人與障礙物各吃一次傷害。
 * 單體輸出不如箭塔，但敵人排成一列時非常划算。
 */
class PiercingShot(
    startX: Float,
    startY: Float,
    private val dirX: Float,
    private val dirY: Float,
    private val maxDistance: Float,
    private val damage: Int,
    private val speed: Float,
    private val hitRadius: Float
) {

    var x = startX
        private set
    var y = startY
        private set
    var isDone = false
        private set

    private var travelled = 0f
    private var age = 0
    private val hitEnemies = HashSet<Enemy>()
    private val hitObstacles = HashSet<Obstacle>()
    private val paint = Paint().apply { isAntiAlias = true }

    fun update(enemies: List<Enemy>, obstacles: List<Obstacle>) {
        if (isDone) return
        age++

        x += dirX * speed
        y += dirY * speed
        travelled += speed

        if (age % 2 == 0) {
            Fx.burst(x, y, 1, Color.parseColor("#FFB59A"), Ui.dp(0.4f), Ui.dp(1.6f), 14, gravity = -0.02f)
        }

        val r2 = hitRadius * hitRadius
        for (enemy in enemies) {
            if (!enemy.isAlive || enemy in hitEnemies) continue
            val dx = enemy.x - x
            val dy = enemy.y - y
            if (dx * dx + dy * dy <= r2) {
                hitEnemies.add(enemy)
                enemy.takeDamage(damage)
                Fx.hitSpark(enemy.x, enemy.y, Color.parseColor("#FF9FB0"))
            }
        }
        for (obstacle in obstacles) {
            if (!obstacle.isAlive || obstacle in hitObstacles) continue
            val dx = obstacle.centerX - x
            val dy = obstacle.centerY - y
            if (dx * dx + dy * dy <= r2) {
                hitObstacles.add(obstacle)
                obstacle.takeDamage(damage, enemies)
            }
        }

        if (travelled >= maxDistance) {
            isDone = true
            Fx.explosion(x, y, hitRadius * 1.6f)
            Audio.play(Sfx.EXPLODE)
        }
    }

    fun draw(canvas: Canvas) {
        if (isDone) return
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#66FFC9B4")
        canvas.drawCircle(x - dirX * Ui.dp(5f), y - dirY * Ui.dp(5f), Ui.dp(2.6f), paint)
        paint.color = Color.parseColor("#FF9FB0")
        canvas.drawCircle(x, y, Ui.dp(3.6f), paint)
        paint.color = Color.parseColor("#FFF3E2")
        canvas.drawCircle(x + dirX * Ui.dp(1.4f), y + dirY * Ui.dp(1.4f), Ui.dp(1.8f), paint)
    }
}
