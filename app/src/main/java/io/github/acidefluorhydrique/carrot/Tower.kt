package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.sin

enum class TowerType(
    val displayNameRes: Int,
    val emoji: String,
    val baseCost: Int,
    val taglineRes: Int,
    val plateColor: String,
    val accentColor: String
) {
    ARROW(R.string.tower_arrow, "🏹", 50, R.string.tower_arrow_tag, "#4A7C59", "#FFD700"),
    ICE(R.string.tower_ice, "❄️", 60, R.string.tower_ice_tag, "#41647C", "#8FD8FF"),
    BOMB(R.string.tower_bomb, "💣", 85, R.string.tower_bomb_tag, "#7C4A4A", "#FF8A3D"),
    POISON(R.string.tower_poison, "☠️", 95, R.string.tower_poison_tag, "#4E6B33", "#A3E635"),
    LIGHT(R.string.tower_tesla, "⚡", 130, R.string.tower_tesla_tag, "#5B4B86", "#C4B5FD");

    val displayName: String get() = Strings.get(displayNameRes)
    val tagline: String get() = Strings.get(taglineRes)

    companion object {
        fun fromName(name: String): TowerType =
            values().firstOrNull { it.name == name } ?: ARROW
    }
}

/** 選敵策略。 */
enum class TargetMode(val labelRes: Int) {
    FIRST(R.string.target_first),
    STRONGEST(R.string.target_strongest),
    CLOSEST(R.string.target_nearest);

    val label: String get() = Strings.get(labelRes)

    fun next(): TargetMode = values()[(ordinal + 1) % values().size]

    companion object {
        fun fromName(name: String): TargetMode =
            values().firstOrNull { it.name == name } ?: FIRST
    }
}

class Tower(
    val col: Int,
    val row: Int,
    val type: TowerType,
    private val gameMap: GameMap
) {

    var level: Int = 1
    var cooldown: Int = 0
    var aimAngle: Float = -45f
    var targetMode: TargetMode = TargetMode.FIRST

    /** 累計投入金幣，決定賣塔回收價。 */
    var invested: Int = type.baseCost

    /** 開火閃光計時。 */
    var fireFlash: Int = 0
        private set

    private var animFrame = 0
    private val paint = Paint().apply { isAntiAlias = true }

    val isMaxLevel: Boolean get() = level >= MAX_LEVEL

    val range: Float
        get() = gameMap.cellSize * when (type) {
            TowerType.ARROW -> 2.4f + level * 0.3f
            TowerType.ICE -> 2.0f + level * 0.3f
            TowerType.BOMB -> 1.9f + level * 0.3f
            TowerType.POISON -> 2.0f + level * 0.25f
            TowerType.LIGHT -> 2.5f + level * 0.35f
        }

    val damage: Int
        get() = when (type) {
            TowerType.ARROW -> 3 + level * 3
            TowerType.ICE -> 1 + level
            TowerType.BOMB -> 4 + level * 5
            TowerType.POISON -> 1 + level
            TowerType.LIGHT -> 3 + level * 3
        }

    val attackInterval: Int
        get() = when (type) {
            TowerType.ARROW -> max(18, 40 - level * 6)
            TowerType.ICE -> max(24, 55 - level * 8)
            TowerType.BOMB -> max(44, 85 - level * 10)
            TowerType.POISON -> max(40, 72 - level * 8)
            TowerType.LIGHT -> max(30, 62 - level * 9)
        }

    val splashRadius: Float get() = gameMap.cellSize * (1.2f + level * 0.2f)
    val slowFactor: Float get() = 0.62f - level * 0.08f
    val slowDuration: Int get() = 60 + level * 20
    val poisonDamage: Int get() = 1 + level
    val poisonDuration: Int get() = 150 + level * 40
    val chainTargets: Int get() = 1 + level

    val upgradeCost: Int
        get() = ((type.baseCost * 1.15f * level) / 5f).toInt() * 5

    val sellValue: Int get() = (invested * 0.65f).toInt()

    /** 面板顯示用的每秒理論傷害。 */
    val dps: Int
        get() = when (type) {
            TowerType.POISON -> poisonDamage * 60 / Enemy.POISON_TICK_FRAMES
            TowerType.LIGHT -> damage * chainTargets * 60 / attackInterval
            else -> damage * 60 / attackInterval
        }

    val centerX: Float get() = gameMap.offsetX + col * gameMap.cellSize + gameMap.cellSize / 2f
    val centerY: Float get() = gameMap.offsetY + row * gameMap.cellSize + gameMap.cellSize / 2f

    fun onFired() {
        fireFlash = 6
        cooldown = attackInterval
    }

    fun tick() {
        animFrame++
        if (cooldown > 0) cooldown--
        if (fireFlash > 0) fireFlash--
    }

    fun upgrade() {
        if (isMaxLevel) return
        invested += upgradeCost
        level++
    }

    // ---- 繪製 ----

    fun draw(canvas: Canvas) {
        val cs = gameMap.cellSize
        val px = gameMap.offsetX + col * cs
        val py = gameMap.offsetY + row * cs
        val inset = cs * 0.06f

        // 底座
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#55000000")
        canvas.drawRoundRect(
            RectF(px + inset, py + inset + cs * 0.05f, px + cs - inset, py + cs - inset + cs * 0.05f),
            cs * 0.2f, cs * 0.2f, paint
        )
        paint.color = Color.parseColor(type.plateColor)
        canvas.drawRoundRect(
            RectF(px + inset, py + inset, px + cs - inset, py + cs - inset),
            cs * 0.2f, cs * 0.2f, paint
        )
        paint.color = Color.parseColor("#33FFFFFF")
        canvas.drawRoundRect(
            RectF(px + inset * 1.6f, py + inset * 1.6f, px + cs - inset * 1.6f, py + cs * 0.42f),
            cs * 0.16f, cs * 0.16f, paint
        )

        // 開火閃光
        if (fireFlash > 0) {
            paint.color = Color.parseColor(type.accentColor)
            paint.alpha = (fireFlash * 26).coerceIn(0, 170)
            canvas.drawCircle(centerX, centerY, cs * 0.44f, paint)
            paint.alpha = 255
        }

        // 塔身
        paint.textSize = cs * 0.5f
        val emoji = type.emoji
        val textWidth = paint.measureText(emoji)
        val recoil = if (fireFlash > 0) cs * 0.04f else 0f

        if (type == TowerType.ARROW) {
            canvas.save()
            canvas.rotate(aimAngle + 45f, centerX, centerY)
            canvas.drawText(emoji, centerX - textWidth / 2f - recoil, centerY + cs * 0.18f, paint)
            canvas.restore()
        } else {
            val float = if (type == TowerType.LIGHT) sin(animFrame * 0.12f) * cs * 0.03f else 0f
            canvas.drawText(emoji, centerX - textWidth / 2f, centerY + cs * 0.18f + float, paint)
        }

        drawLevelPips(canvas, px, py, cs)
    }

    private fun drawLevelPips(canvas: Canvas, px: Float, py: Float, cs: Float) {
        if (level <= 1) return
        paint.style = Paint.Style.FILL
        val r = cs * 0.055f
        val gap = r * 2.6f
        val totalW = gap * (level - 1)
        var cx = px + cs / 2f - totalW / 2f
        val cy = py + cs - cs * 0.11f
        for (i in 0 until level) {
            paint.color = Color.parseColor("#55000000")
            canvas.drawCircle(cx, cy + r * 0.3f, r, paint)
            paint.color = Color.parseColor(type.accentColor)
            canvas.drawCircle(cx, cy, r, paint)
            cx += gap
        }
    }

    companion object {
        const val MAX_LEVEL = 3
    }
}
