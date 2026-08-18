// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

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
    val accentColor: String,
    /** 從第幾關（全域編號）開始可以使用。 */
    val unlockAtLevel: Int
) {
    ARROW(R.string.tower_arrow, "🏹", 50, R.string.tower_arrow_tag, "#4A7C59", "#FFD700", 1),
    ICE(R.string.tower_ice, "❄️", 60, R.string.tower_ice_tag, "#41647C", "#8FD8FF", 2),
    BOMB(R.string.tower_bomb, "💣", 85, R.string.tower_bomb_tag, "#7C4A4A", "#FF8A3D", 5),
    MOON(R.string.tower_moon, "🌙", 75, R.string.tower_moon_tag, "#48507E", "#C7D2FE", 7),
    POISON(R.string.tower_poison, "☠️", 95, R.string.tower_poison_tag, "#4E6B33", "#A3E635", 9),
    ROCKET(R.string.tower_rocket, "🚀", 120, R.string.tower_rocket_tag, "#6B4A57", "#FF9FB0", 11),
    LIGHT(R.string.tower_tesla, "⚡", 130, R.string.tower_tesla_tag, "#5B4B86", "#C4B5FD", 13),
    SUN(R.string.tower_sun, "☀️", 110, R.string.tower_sun_tag, "#8A6A2E", "#FFD75E", 15);

    val displayName: String get() = Strings.get(displayNameRes)
    val tagline: String get() = Strings.get(taglineRes)

    /** 以自己為圓心的範圍脈衝，不需要瞄準。 */
    val isAreaPulse: Boolean get() = this == SUN || this == MOON

    /** 直線穿透。 */
    val isPiercing: Boolean get() = this == ROCKET

    /** 無指向的傷害會順手掃到障礙物，不必玩家指定集火。 */
    val hitsObstaclesPassively: Boolean get() = isAreaPulse || isPiercing

    val rotatesToTarget: Boolean get() = this == ARROW || this == LIGHT || this == ROCKET

    /** 關卡是為「當時手上有的塔」平衡的，所以以目前這一關的編號判斷。 */
    val isUnlocked: Boolean get() = GameState.level.id >= unlockAtLevel

    /** 這一關的配置是否提供這座塔。 */
    val isAvailable: Boolean get() = isUnlocked && this in GameState.level.allowedTowers

    /** 射程，單位是格。預覽圈與實際射程共用這一份公式。 */
    fun rangeCells(level: Int): Float = when (this) {
        ARROW -> 2.4f + level * 0.3f
        ICE -> 2.0f + level * 0.3f
        BOMB -> 1.9f + level * 0.3f
        MOON -> 2.7f + level * 0.35f
        POISON -> 2.0f + level * 0.25f
        ROCKET -> 3.2f + level * 0.4f
        LIGHT -> 2.5f + level * 0.35f
        SUN -> 1.6f + level * 0.3f
    }

    /** 「1-2」這樣的解鎖提示。 */
    val unlockHint: String
        get() {
            val level = GameLevels.byId(unlockAtLevel)
            return Strings.format(R.string.tower_locked, level.chapterId, level.indexInChapter)
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

    var fireFlash: Int = 0
        private set

    private var animFrame = 0
    private val paint = Paint().apply { isAntiAlias = true }

    val isMaxLevel: Boolean get() = level >= MAX_LEVEL

    val range: Float get() = gameMap.cellSize * type.rangeCells(level)

    val damage: Int
        get() = when (type) {
            TowerType.ARROW -> 3 + level * 3
            TowerType.ICE -> 1 + level
            TowerType.BOMB -> 4 + level * 5
            TowerType.MOON -> 1
            TowerType.POISON -> 1 + level
            TowerType.ROCKET -> 8 + level * 7
            TowerType.LIGHT -> 3 + level * 3
            TowerType.SUN -> 2 + level * 2
        }

    val attackInterval: Int
        get() = when (type) {
            TowerType.ARROW -> max(18, 40 - level * 6)
            TowerType.ICE -> max(24, 55 - level * 8)
            TowerType.BOMB -> max(44, 85 - level * 10)
            TowerType.MOON -> max(46, 70 - level * 6)
            TowerType.POISON -> max(40, 72 - level * 8)
            TowerType.ROCKET -> max(70, 120 - level * 13)
            TowerType.LIGHT -> max(30, 62 - level * 9)
            TowerType.SUN -> max(40, 66 - level * 7)
        }

    val splashRadius: Float get() = gameMap.cellSize * (1.2f + level * 0.2f)
    val slowFactor: Float get() = 0.62f - level * 0.08f
    val slowDuration: Int get() = 60 + level * 20

    /** 月亮：範圍大但只是輕度減速，和冰塔的單點強控互補。 */
    val auraSlowFactor: Float get() = 0.86f - level * 0.06f
    val auraSlowDuration: Int get() = 70 + level * 20

    val poisonDamage: Int get() = 1 + level
    val poisonDuration: Int get() = 150 + level * 40
    val chainTargets: Int get() = 1 + level

    val upgradeCost: Int
        get() = ((type.baseCost * 1.15f * level) / 5f).toInt() * 5

    val sellValue: Int get() = (invested * 0.65f).toInt()

    /** 面板顯示用的每秒理論傷害（單一目標）。 */
    val dps: Int
        get() = when (type) {
            TowerType.POISON -> poisonDamage * 60 / Enemy.POISON_TICK_FRAMES
            TowerType.LIGHT -> damage * chainTargets * 60 / attackInterval
            TowerType.MOON -> 0
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

        // 範圍脈衝塔平常就淡淡顯示作用範圍，玩家才知道它罩得到哪
        if (type.isAreaPulse) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = Ui.dp(1f)
            paint.color = Color.parseColor(type.accentColor)
            paint.alpha = 46
            canvas.drawCircle(centerX, centerY, range, paint)
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }

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

        if (fireFlash > 0) {
            paint.color = Color.parseColor(type.accentColor)
            paint.alpha = (fireFlash * 26).coerceIn(0, 170)
            canvas.drawCircle(centerX, centerY, cs * 0.44f, paint)
            paint.alpha = 255
        }

        paint.textSize = cs * 0.5f
        val emoji = type.emoji
        val textWidth = paint.measureText(emoji)
        val recoil = if (fireFlash > 0) cs * 0.04f else 0f

        if (type.rotatesToTarget) {
            canvas.save()
            canvas.rotate(aimAngle + 45f, centerX, centerY)
            canvas.drawText(emoji, centerX - textWidth / 2f - recoil, centerY + cs * 0.18f, paint)
            canvas.restore()
        } else {
            val float = if (type == TowerType.LIGHT || type.isAreaPulse) {
                sin(animFrame * 0.12f) * cs * 0.03f
            } else 0f
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
