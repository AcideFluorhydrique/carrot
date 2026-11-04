// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class Enemy(
    private val gameMap: GameMap,
    val kind: EnemyKind,
    maxHpValue: Int,
    baseSpeedValue: Float,
    rewardValue: Int
) {

    private var pathIndex = 0

    var distanceTravelled = 0f
        private set
    var x = 0f
        private set
    var y = 0f
        private set

    var hp: Int = maxHpValue
        private set
    var maxHp: Int = maxHpValue
        private set
    var baseSpeed: Float = baseSpeedValue
        private set
    var goldReward: Int = rewardValue
        private set

    var isDead = false
        private set
    var hasReachedEnd = false
        private set

    private var slowFactor = 1f
    private var slowTimer = 0

    /** 冰彈：凍住時完全不動，解凍後另有一段自己的減速，見 [freeze]。 */
    private var frozenTimer = 0
    private var chillFactor = 1f
    private var chillTimer = 0

    private var poisonDamage = 0
    private var poisonTimer = 0
    private var poisonTick = 0

    private var hitFlash = 0
    private val bobPhase = Random.nextFloat() * 6.28f
    private var animFrame = 0

    /**
     * 移動速度以格寬為基準換算，否則同一組數值在不同解析度上
     * 會變成完全不同的難度（格子越小、過關越快）。
     */
    val speed: Float get() = baseSpeed * currentSlow * gameMap.cellSize / REFERENCE_CELL

    /**
     * 目前的速度倍率。凍住是 0；否則塔的減速和冰彈的餘寒取最強（最小）的那個，不相乘。
     * 要改成疊加或別的算法，只改這裡。
     */
    private val currentSlow: Float
        get() = when {
            frozenTimer > 0 -> 0f
            chillTimer > 0 && slowTimer > 0 -> minOf(slowFactor, chillFactor)
            chillTimer > 0 -> chillFactor
            slowTimer > 0 -> slowFactor
            else -> 1f
        }

    val isSlowed: Boolean get() = slowTimer > 0 || chillTimer > 0
    val isFrozen: Boolean get() = frozenTimer > 0
    val isPoisoned: Boolean get() = poisonTimer > 0
    val hpRatio: Float get() = if (maxHp <= 0) 0f else (hp.toFloat() / maxHp).coerceIn(0f, 1f)
    val radius: Float get() = gameMap.cellSize * 0.3f * kind.sizeScale
    val isAlive: Boolean get() = !isDead && !hasReachedEnd

    init {
        if (gameMap.pathPoints.isNotEmpty()) {
            val (col, row) = gameMap.pathPoints[0]
            val (px, py) = gameMap.centerOf(col, row)
            x = px
            y = py
        }
    }

    // ---- 狀態效果 ----

    /**
     * 套用減速。factor 越小越慢。
     *
     * 較弱的減速不能延長較強的那一個。舊寫法是「強度取較強、時間取較長」，
     * 但時間比的是「新的持續時間 vs 目前剩餘時間」，於是月亮那種每秒掃一次、
     * 只有 0.80x 的微弱光環，會不斷把冰塔 0.54x 的計時器補滿，
     * 讓一發冰彈的強控在月亮罩得到的範圍內變成永久狀態。
     */
    fun applySlow(factor: Float, duration: Int) {
        if (slowTimer <= 0) {
            slowFactor = factor
            slowTimer = duration
            return
        }
        if (factor < slowFactor) {
            // 更強的減速接管，時間取兩者較長者
            slowFactor = factor
            if (duration > slowTimer) slowTimer = duration
            return
        }
        if (factor > slowFactor) return
        // 同強度的來源：單純續期
        if (duration > slowTimer) slowTimer = duration
    }

    /**
     * 冰彈：原地凍 [frames] 影格，解凍後再以 [chill] 的倍率慢 [chillFrames] 影格。首領也一樣。
     * 已經凍住時取較長的那次，不累加；餘寒同理。
     */
    fun freeze(frames: Int, chill: Float, chillFrames: Int) {
        if (!isAlive) return
        frozenTimer = maxOf(frozenTimer, frames)
        chillFactor = minOf(if (chillTimer > 0) chillFactor else 1f, chill)
        chillTimer = maxOf(chillTimer, frames + chillFrames)
        hitFlash = 6
    }

    fun applyPoison(damagePerTick: Int, duration: Int) {
        if (damagePerTick > poisonDamage) poisonDamage = damagePerTick
        if (duration > poisonTimer) poisonTimer = duration
    }

    fun takeDamage(amount: Int, ignoreArmor: Boolean = false, showNumber: Boolean = true) {
        if (!isAlive) return
        val reduced = if (ignoreArmor) amount else (amount - kind.armor).coerceAtLeast(1)
        hp -= reduced
        hitFlash = 6
        if (showNumber) Fx.damage(x, y - radius, reduced, kind.isBoss)
        if (hp <= 0) die()
    }

    /**
     * 核彈：一般敵人直接消滅，首領扣掉最大血量的 [bossFraction]。都不給金幣，見 [Nuke]。
     */
    fun nuke(bossFraction: Float) {
        if (!isAlive) return
        if (!kind.isBoss) {
            die(grantGold = false)
            return
        }
        val amount = (maxHp * bossFraction).toInt().coerceAtLeast(1)
        hp -= amount
        hitFlash = 6
        Fx.damage(x, y - radius, amount, critical = true)
        if (hp <= 0) die(grantGold = false)
    }

    private fun die(grantGold: Boolean = true) {
        if (isDead) return
        hp = 0
        isDead = true
        GameState.kills++
        Fx.deathPuff(x, y, Colors.of(kind.auraColor), kind.sizeScale)
        if (grantGold) {
            GameState.addGold(goldReward)
            Fx.goldGain(x, y - radius, goldReward)
        }
        if (kind.isBoss) {
            Fx.explosion(x, y, gameMap.cellSize * 1.6f)
            Fx.shake(Ui.dp(6f), 22)
            Audio.play(Sfx.EXPLODE)
        } else {
            Audio.play(Sfx.DIE)
        }
    }

    // ---- 更新 ----

    fun update() {
        if (!isAlive) return
        // 凍住時連走路的晃動也停下來
        if (frozenTimer <= 0) animFrame++

        if (slowTimer > 0) {
            slowTimer--
            if (slowTimer == 0) slowFactor = 1f
        }
        if (frozenTimer > 0) frozenTimer--
        if (chillTimer > 0) {
            chillTimer--
            if (chillTimer == 0) chillFactor = 1f
        }

        if (poisonTimer > 0) {
            poisonTimer--
            poisonTick++
            if (poisonTick >= POISON_TICK_FRAMES) {
                poisonTick = 0
                Fx.burst(x, y, 3, Colors.of("#84CC16"), Ui.dp(0.9f), Ui.dp(1.6f), 16, gravity = -0.03f)
                takeDamage(poisonDamage, ignoreArmor = true, showNumber = false)
                if (!isAlive) return
            }
            if (poisonTimer == 0) poisonDamage = 0
        }

        if (hitFlash > 0) hitFlash--

        val points = gameMap.pathPoints
        if (pathIndex >= points.size - 1) {
            hasReachedEnd = true
            GameState.onEnemyReached(kind, hpRatio)
            Fx.burst(x, y, 10, Colors.of("#F87171"), Ui.dp(2f), Ui.dp(2.6f), 24)
            return
        }

        val (targetCol, targetRow) = points[pathIndex + 1]
        val (targetX, targetY) = gameMap.centerOf(targetCol, targetRow)

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)
        val step = speed
        if (step <= 0f) return

        if (dist <= step || dist < 0.0001f) {
            x = targetX
            y = targetY
            pathIndex++
            distanceTravelled += dist
        } else {
            x += dx / dist * step
            y += dy / dist * step
            distanceTravelled += step
        }
    }

    // ---- 存檔 ----

    fun snapshot(): EnemySnapshot = EnemySnapshot(
        kind = kind.name,
        pathIndex = pathIndex,
        distanceTravelled = distanceTravelled,
        x = x,
        y = y,
        hp = hp,
        maxHp = maxHp,
        baseSpeed = baseSpeed,
        goldReward = goldReward,
        slowFactor = slowFactor,
        slowTimer = slowTimer,
        poisonDamage = poisonDamage,
        poisonTimer = poisonTimer,
        frozenTimer = frozenTimer,
        chillFactor = chillFactor,
        chillTimer = chillTimer
    )

    fun restore(snapshot: EnemySnapshot) {
        pathIndex = snapshot.pathIndex
        distanceTravelled = snapshot.distanceTravelled
        x = snapshot.x
        y = snapshot.y
        hp = snapshot.hp
        maxHp = snapshot.maxHp
        baseSpeed = snapshot.baseSpeed
        goldReward = snapshot.goldReward
        slowFactor = snapshot.slowFactor
        slowTimer = snapshot.slowTimer
        poisonDamage = snapshot.poisonDamage
        poisonTimer = snapshot.poisonTimer
        frozenTimer = snapshot.frozenTimer
        chillFactor = snapshot.chillFactor
        chillTimer = snapshot.chillTimer
        poisonTick = 0
        hitFlash = 0
        isDead = false
        hasReachedEnd = false
    }

    // ---- 繪製 ----

    private val paint = Paint().apply { isAntiAlias = true }
    /** 畫完就丟的形狀共用這個，避免每幀配置。 */
    private val scratch = RectF()

    fun draw(canvas: Canvas) {
        if (!isAlive) return

        val r = radius
        val bob = sin(animFrame * 0.22f + bobPhase) * r * 0.14f

        // 影子
        paint.style = Paint.Style.FILL
        paint.color = Colors.of("#40000000")
        scratch.set(x - r * 0.8f, y + r * 0.6f, x + r * 0.8f, y + r * 0.95f)
        canvas.drawOval(scratch, paint)

        // 狀態光暈
        if (isSlowed) {
            paint.color = Colors.of("#5544AAFF")
            canvas.drawCircle(x, y + bob, r * 1.35f, paint)
        }
        if (isPoisoned) {
            paint.color = Colors.of("#5584CC16")
            canvas.drawCircle(x, y + bob, r * 1.2f, paint)
        }
        if (kind.isBoss) {
            paint.color = Colors.of("#33F87171")
            canvas.drawCircle(x, y + bob, r * 1.5f, paint)
        }

        // 本體。彩色 emoji 會套用畫筆的透明度，不重設的話會沿用影子或光暈的半透明色
        paint.color = Color.WHITE
        paint.textSize = r * 2.1f
        val emoji = kind.emoji
        canvas.drawText(emoji, x - TextWidths.of(emoji, paint) / 2f, y + bob + r * 0.72f, paint)

        // 冰彈：凍住時整隻罩一層冰
        if (frozenTimer > 0) {
            paint.color = Colors.of("#99D6F0FF")
            canvas.drawCircle(x, y + bob, r * 1.1f, paint)
        }

        // 受擊白光
        if (hitFlash > 0) {
            paint.color = Color.WHITE
            paint.alpha = (hitFlash * 26).coerceIn(0, 200)
            canvas.drawCircle(x, y + bob, r, paint)
            paint.alpha = 255
        }

        // 護甲標記
        if (kind.armor > 0) {
            paint.textSize = r * 0.7f
            canvas.drawText("🛡", x + r * 0.55f, y + bob - r * 0.35f, paint)
        }

        drawHealthBar(canvas, r, bob)
    }

    private fun drawHealthBar(canvas: Canvas, r: Float, bob: Float) {
        val barW = r * 2.2f
        val barH = Ui.dp(3.2f)
        val left = x - barW / 2f
        val top = y + bob - r - Ui.dp(6f)

        paint.style = Paint.Style.FILL
        paint.color = Colors.of("#99101010")
        scratch.set(left - 1f, top - 1f, left + barW + 1f, top + barH + 1f)
        canvas.drawRoundRect(scratch, barH, barH, paint)

        paint.color = when {
            isPoisoned -> Colors.of("#A3E635")
            isSlowed -> Colors.of("#44AAFF")
            hpRatio > 0.5f -> Colors.of("#4ADE80")
            hpRatio > 0.25f -> Colors.of("#FACC15")
            else -> Colors.of("#F87171")
        }
        scratch.set(left, top, left + barW * hpRatio, top + barH)
        canvas.drawRoundRect(scratch, barH, barH, paint)
    }

    companion object {
        const val POISON_TICK_FRAMES = 18

        /** 速度數值的基準格寬。 */
        private const val REFERENCE_CELL = 60f
    }
}
