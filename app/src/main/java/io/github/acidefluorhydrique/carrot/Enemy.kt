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
    val speed: Float get() = baseSpeed * slowFactor * gameMap.cellSize / REFERENCE_CELL
    val isSlowed: Boolean get() = slowTimer > 0
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

    fun applySlow(factor: Float, duration: Int) {
        // 取更強的減速，時間取較長者
        if (factor < slowFactor || slowTimer <= 0) slowFactor = factor
        if (duration > slowTimer) slowTimer = duration
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

    private fun die() {
        if (isDead) return
        hp = 0
        isDead = true
        GameState.kills++
        GameState.addGold(goldReward)
        Fx.deathPuff(x, y, Color.parseColor(kind.auraColor), kind.sizeScale)
        Fx.goldGain(x, y - radius, goldReward)
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
        animFrame++

        if (slowTimer > 0) {
            slowTimer--
            if (slowTimer == 0) slowFactor = 1f
        }

        if (poisonTimer > 0) {
            poisonTimer--
            poisonTick++
            if (poisonTick >= POISON_TICK_FRAMES) {
                poisonTick = 0
                Fx.burst(x, y, 3, Color.parseColor("#84CC16"), Ui.dp(0.9f), Ui.dp(1.6f), 16, gravity = -0.03f)
                takeDamage(poisonDamage, ignoreArmor = true, showNumber = false)
                if (!isAlive) return
            }
            if (poisonTimer == 0) poisonDamage = 0
        }

        if (hitFlash > 0) hitFlash--

        val points = gameMap.pathPoints
        if (pathIndex >= points.size - 1) {
            hasReachedEnd = true
            GameState.onEnemyReached(kind)
            Fx.burst(x, y, 10, Color.parseColor("#F87171"), Ui.dp(2f), Ui.dp(2.6f), 24)
            return
        }

        val (targetCol, targetRow) = points[pathIndex + 1]
        val (targetX, targetY) = gameMap.centerOf(targetCol, targetRow)

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)
        val step = speed

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
        poisonTimer = poisonTimer
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
        poisonTick = 0
        hitFlash = 0
        isDead = false
        hasReachedEnd = false
    }

    // ---- 繪製 ----

    private val paint = Paint().apply { isAntiAlias = true }

    fun draw(canvas: Canvas) {
        if (!isAlive) return

        val r = radius
        val bob = sin(animFrame * 0.22f + bobPhase) * r * 0.14f

        // 影子
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#40000000")
        canvas.drawOval(RectF(x - r * 0.8f, y + r * 0.6f, x + r * 0.8f, y + r * 0.95f), paint)

        // 狀態光暈
        if (isSlowed) {
            paint.color = Color.parseColor("#5544AAFF")
            canvas.drawCircle(x, y + bob, r * 1.35f, paint)
        }
        if (isPoisoned) {
            paint.color = Color.parseColor("#5584CC16")
            canvas.drawCircle(x, y + bob, r * 1.2f, paint)
        }
        if (kind.isBoss) {
            paint.color = Color.parseColor("#33F87171")
            canvas.drawCircle(x, y + bob, r * 1.5f, paint)
        }

        // 本體
        paint.textSize = r * 2.1f
        val emoji = kind.emoji
        canvas.drawText(emoji, x - paint.measureText(emoji) / 2f, y + bob + r * 0.72f, paint)

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
        paint.color = Color.parseColor("#99101010")
        canvas.drawRoundRect(RectF(left - 1f, top - 1f, left + barW + 1f, top + barH + 1f), barH, barH, paint)

        paint.color = when {
            isPoisoned -> Color.parseColor("#A3E635")
            isSlowed -> Color.parseColor("#44AAFF")
            hpRatio > 0.5f -> Color.parseColor("#4ADE80")
            hpRatio > 0.25f -> Color.parseColor("#FACC15")
            else -> Color.parseColor("#F87171")
        }
        canvas.drawRoundRect(RectF(left, top, left + barW * hpRatio, top + barH), barH, barH, paint)
    }

    companion object {
        const val POISON_TICK_FRAMES = 18

        /** 速度數值的基準格寬。 */
        private const val REFERENCE_CELL = 60f
    }
}
