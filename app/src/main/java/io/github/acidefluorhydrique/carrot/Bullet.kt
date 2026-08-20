// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.sqrt

/**
 * 飛行中的投射物。電塔是瞬發連鎖，不走這裡。
 */
class Bullet(
    startX: Float,
    startY: Float,
    private val target: Enemy,
    private val damage: Int,
    val type: TowerType,
    private val speed: Float,
    private val splashRadius: Float = 0f,
    private val slowFactor: Float = 1f,
    private val slowDuration: Int = 0,
    private val poisonDamage: Int = 0,
    private val poisonDuration: Int = 0
) {

    var x = startX
        private set
    var y = startY
        private set
    var isDone = false
        private set

    private var lastTargetX = target.x
    private var lastTargetY = target.y
    private var dirX = 0f
    private var dirY = -1f
    private var age = 0

    private val paint = Paint().apply { isAntiAlias = true }

    fun update(enemies: List<Enemy>) {
        if (isDone) return
        age++

        if (!target.isAlive) {
            // 目標已消失：炸彈仍在原地爆開，其餘直接消散
            if (type == TowerType.BOMB) {
                explode(enemies, lastTargetX, lastTargetY)
            } else {
                isDone = true
            }
            return
        }

        lastTargetX = target.x
        lastTargetY = target.y

        val dx = target.x - x
        val dy = target.y - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > 0.0001f) {
            dirX = dx / dist
            dirY = dy / dist
        }

        if (dist <= speed) {
            x = target.x
            y = target.y
            onHit(enemies)
        } else {
            x += dirX * speed
            y += dirY * speed
            spawnTrail()
        }

        // 保險：飛太久就自我了斷，避免殘留物件
        if (age > 300) isDone = true
    }

    private fun spawnTrail() {
        when (type) {
            TowerType.BOMB -> if (age % 2 == 0) {
                Fx.burst(x, y, 1, Colors.of("#8A8A8A"), Ui.dp(0.3f), Ui.dp(1.4f), 14, gravity = -0.02f)
            }
            TowerType.POISON -> if (age % 2 == 0) {
                Fx.burst(x, y, 1, Colors.of("#84CC16"), Ui.dp(0.3f), Ui.dp(1.5f), 16, gravity = -0.02f)
            }
            TowerType.ICE -> if (age % 3 == 0) {
                Fx.burst(x, y, 1, Colors.of("#BFEAFF"), Ui.dp(0.3f), Ui.dp(1.2f), 12, gravity = 0f)
            }
            else -> Unit
        }
    }

    private fun onHit(enemies: List<Enemy>) {
        isDone = true
        when (type) {
            TowerType.ARROW -> {
                target.takeDamage(damage)
                Fx.hitSpark(x, y, Colors.of("#FFD700"))
                Audio.play(Sfx.HIT)
            }
            TowerType.BOMB -> explode(enemies, x, y)
            TowerType.ICE -> {
                target.takeDamage(damage)
                target.applySlow(slowFactor, slowDuration)
                Fx.frost(x, y, Ui.dp(14f))
                Audio.play(Sfx.ICE)
            }
            TowerType.POISON -> {
                target.takeDamage(damage, ignoreArmor = true)
                target.applyPoison(poisonDamage, poisonDuration)
                Fx.burst(x, y, 8, Colors.of("#84CC16"), Ui.dp(1.4f), Ui.dp(2f), 20, gravity = -0.02f)
                Audio.play(Sfx.POISON)
            }
            // 電塔是瞬發、太陽月亮是範圍脈衝、火箭是直線穿透，都不走這裡
            TowerType.LIGHT, TowerType.MOON, TowerType.ROCKET, TowerType.SUN -> {
                target.takeDamage(damage)
            }
        }
    }

    private fun explode(enemies: List<Enemy>, cx: Float, cy: Float) {
        isDone = true
        Fx.explosion(cx, cy, splashRadius)
        Audio.play(Sfx.EXPLODE)
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            val dx = enemy.x - cx
            val dy = enemy.y - cy
            if (sqrt(dx * dx + dy * dy) <= splashRadius) {
                enemy.takeDamage(damage)
            }
        }
    }

    fun draw(canvas: Canvas) {
        if (isDone) return
        paint.style = Paint.Style.FILL
        when (type) {
            TowerType.ARROW -> {
                val len = Ui.dp(7f)
                paint.strokeWidth = Ui.dp(1.8f)
                paint.style = Paint.Style.STROKE
                paint.color = Colors.of("#66FFE9A8")
                canvas.drawLine(x - dirX * len * 1.8f, y - dirY * len * 1.8f, x, y, paint)
                paint.color = Colors.of("#FFD700")
                canvas.drawLine(x - dirX * len, y - dirY * len, x + dirX * len * 0.5f, y + dirY * len * 0.5f, paint)
                paint.style = Paint.Style.FILL
            }
            TowerType.BOMB -> {
                paint.color = Colors.of("#33221F")
                canvas.drawCircle(x, y, Ui.dp(4f), paint)
                paint.color = Colors.of("#FF8A3D")
                canvas.drawCircle(x - dirX * Ui.dp(4f), y - dirY * Ui.dp(4f), Ui.dp(1.6f), paint)
            }
            TowerType.ICE -> {
                paint.color = Colors.of("#CCE8FAFF")
                canvas.drawCircle(x, y, Ui.dp(3.2f), paint)
                paint.color = Colors.of("#8FD8FF")
                canvas.drawCircle(x, y, Ui.dp(2f), paint)
            }
            TowerType.POISON -> {
                paint.color = Colors.of("#A3E635")
                canvas.drawCircle(x, y, Ui.dp(3.4f), paint)
                paint.color = Colors.of("#65A30D")
                canvas.drawCircle(x + Ui.dp(0.8f), y + Ui.dp(0.8f), Ui.dp(1.6f), paint)
            }
            TowerType.LIGHT, TowerType.MOON, TowerType.ROCKET, TowerType.SUN -> {
                paint.color = Colors.of("#C4B5FD")
                canvas.drawCircle(x, y, Ui.dp(3f), paint)
            }
        }
    }
}
