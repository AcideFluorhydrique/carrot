// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

/**
 * 可摧毀的障礙物種類。血量與獎勵都以關卡的基礎數值縮放，
 * 才不會打到後期還在清一堆不值得的雜物。
 */
enum class ObstacleKind(
    val emoji: String,
    val displayNameRes: Int,
    val hpFactor: Float,
    val goldFactor: Float,
    val tint: String
) {
    MUSHROOM("🍄", R.string.obstacle_mushroom, 0.5f, 1.2f, "#E05A5A"),
    ROCK("🪨", R.string.obstacle_rock, 1.0f, 1.8f, "#94A3B8"),
    TREE("🌳", R.string.obstacle_tree, 1.5f, 2.4f, "#4A8A48"),
    ICE_BLOCK("🧊", R.string.obstacle_ice, 1.1f, 1.6f, "#8FD8FF"),
    TOXIC("🍄‍🟫", R.string.obstacle_toxic, 0.6f, 1.2f, "#A3E635"),
    CRATE("📦", R.string.obstacle_crate, 2.6f, 8.0f, "#D7A331");

    val displayName: String get() = Strings.get(displayNameRes)

    companion object {
        fun fromName(name: String): ObstacleKind =
            values().firstOrNull { it.name == name } ?: ROCK
    }
}

/** 關卡資料裡的障礙物擺放。 */
data class ObstacleSpec(val col: Int, val row: Int, val kind: ObstacleKind)

class Obstacle(
    val col: Int,
    val row: Int,
    val kind: ObstacleKind,
    val maxHp: Int,
    val goldReward: Int,
    private val gameMap: GameMap
) {

    var hp: Int = maxHp
        private set
    var isDestroyed = false
        private set

    /** 玩家指定為集火目標。 */
    var isFocused = false

    private var hitFlash = 0
    private var animFrame = 0

    val centerX: Float get() = gameMap.offsetX + col * gameMap.cellSize + gameMap.cellSize / 2f
    val centerY: Float get() = gameMap.offsetY + row * gameMap.cellSize + gameMap.cellSize / 2f
    val hpRatio: Float get() = if (maxHp <= 0) 0f else (hp.toFloat() / maxHp).coerceIn(0f, 1f)
    val isAlive: Boolean get() = !isDestroyed

    fun restoreHp(value: Int) {
        hp = value.coerceIn(0, maxHp)
        if (hp == 0) isDestroyed = true
    }

    fun tick() {
        animFrame++
        if (hitFlash > 0) hitFlash--
    }

    fun takeDamage(amount: Int, enemies: List<Enemy>) {
        if (isDestroyed || amount <= 0) return
        hp -= amount
        hitFlash = 5
        Fx.hitSpark(centerX, centerY, Colors.of(kind.tint))
        if (hp <= 0) destroy(enemies)
    }

    private fun destroy(enemies: List<Enemy>) {
        hp = 0
        isDestroyed = true
        isFocused = false
        gameMap.clearObstacle(col, row)
        GameState.addGold(goldReward)
        GameState.obstaclesCleared++

        val color = Colors.of(kind.tint)
        Fx.burst(centerX, centerY, 18, color, Ui.dp(2.2f), Ui.dp(2.6f), 28)
        Fx.ring(centerX, centerY, gameMap.cellSize * 0.8f, color, 20)
        Fx.goldGain(centerX, centerY, goldReward)
        Audio.play(if (kind == ObstacleKind.CRATE) Sfx.COIN else Sfx.EXPLODE)

        when (kind) {
            // 冰塊碎裂：凍住附近的敵人
            ObstacleKind.ICE_BLOCK -> {
                val radius = gameMap.cellSize * 1.6f
                Fx.frost(centerX, centerY, radius)
                affectNearby(enemies, radius) { it.applySlow(0.5f, 120) }
            }
            // 毒菇碎裂：放出一團毒雲
            ObstacleKind.TOXIC -> {
                val radius = gameMap.cellSize * 1.6f
                Fx.ring(centerX, centerY, radius, Colors.of("#A3E635"), 24)
                Fx.burst(centerX, centerY, 16, Colors.of("#84CC16"), Ui.dp(1.6f), Ui.dp(2.4f), 34, gravity = -0.03f)
                affectNearby(enemies, radius) { it.applyPoison(3, 200) }
            }
            ObstacleKind.CRATE -> Fx.shake(Ui.dp(3f), 14)
            else -> Unit
        }
    }

    private inline fun affectNearby(enemies: List<Enemy>, radius: Float, action: (Enemy) -> Unit) {
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            val dx = enemy.x - centerX
            val dy = enemy.y - centerY
            if (dx * dx + dy * dy <= radius * radius) action(enemy)
        }
    }

    // ---- 繪製 ----

    private val paint = Paint().apply { isAntiAlias = true }
    /** 畫完就丟的形狀共用這個，避免每幀配置。 */
    private val scratch = RectF()

    fun draw(canvas: Canvas) {
        if (isDestroyed) return
        val cs = gameMap.cellSize
        val px = gameMap.offsetX + col * cs
        val py = gameMap.offsetY + row * cs

        // 集火中：脈動外框
        if (isFocused) {
            val pulse = 1f + 0.07f * sin(animFrame * 0.16f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = Ui.dp(2f)
            paint.color = Colors.of("#FFE08A")
            scratch.set(px + cs * 0.05f, py + cs * 0.05f, px + cs * 0.95f, py + cs * 0.95f)
            canvas.drawRoundRect(scratch, cs * 0.18f * pulse, cs * 0.18f * pulse, paint)
            paint.style = Paint.Style.FILL
        }

        paint.style = Paint.Style.FILL
        paint.textSize = cs * 0.58f
        val shake = if (hitFlash > 0) sin(animFrame * 2.2f) * cs * 0.04f else 0f
        canvas.drawText(kind.emoji, centerX - paint.measureText(kind.emoji) / 2f + shake, centerY + cs * 0.2f, paint)

        if (hitFlash > 0) {
            paint.color = Color.WHITE
            paint.alpha = (hitFlash * 30).coerceIn(0, 190)
            canvas.drawCircle(centerX, centerY, cs * 0.3f, paint)
            paint.alpha = 255
        }

        // 只在受損或被集火時顯示血條，平常保持乾淨
        if (hp < maxHp || isFocused) {
            val barW = cs * 0.62f
            val barH = Ui.dp(3f)
            val left = centerX - barW / 2f
            val top = py + cs * 0.86f
            paint.color = Colors.of("#99101010")
            scratch.set(left - 1f, top - 1f, left + barW + 1f, top + barH + 1f)
            canvas.drawRoundRect(scratch, barH, barH, paint)
            paint.color = Colors.of(kind.tint)
            scratch.set(left, top, left + barW * hpRatio, top + barH)
            canvas.drawRoundRect(scratch, barH, barH, paint)
        }
    }
}
