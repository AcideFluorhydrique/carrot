// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sqrt

class TowerManager(private val gameMap: GameMap) {

    val towers = mutableListOf<Tower>()
    val bullets = mutableListOf<Bullet>()
    val shots = mutableListOf<PiercingShot>()

    /** 目前選取要建造的塔種（null = 未選）。 */
    var selectedType: TowerType? = null
        private set

    /** 目前選取的既有塔。 */
    var selectedTower: Tower? = null
        private set

    private var ghostCol = -1
    private var ghostRow = -1
    private var placing = false

    private val paint = Paint().apply { isAntiAlias = true }
    /** 畫完就丟的形狀共用這個，避免每幀配置。回傳出去的版面矩形不適用。 */
    private val scratch = RectF()

    val ghostVisible: Boolean get() = placing && gameMap.isValidCell(ghostCol, ghostRow)

    fun reset() {
        towers.clear()
        bullets.clear()
        shots.clear()
        selectedType = null
        selectedTower = null
        cancelPlacement()
    }

    // ---- 建造流程（按下拖曳定位、放開才落塔）----

    fun toggleBuildType(type: TowerType) {
        if (!type.isAvailable) {
            Audio.play(Sfx.DENY)
            return
        }
        selectedType = if (selectedType == type) null else type
        selectedTower = null
        cancelPlacement()
        Audio.play(if (selectedType == null) Sfx.SELL else Sfx.BUILD)
    }

    fun clearBuildType() {
        selectedType = null
        cancelPlacement()
    }

    fun clearSelection() {
        selectedTower = null
    }

    /** 手指按在地圖上。回傳 true 表示已被建造流程接管。 */
    fun onMapDown(x: Float, y: Float): Boolean {
        val (col, row) = gameMap.pixelToCell(x, y)
        if (!gameMap.isValidCell(col, row)) {
            selectedTower = null
            return false
        }

        val existing = towers.firstOrNull { it.col == col && it.row == row }
        if (existing != null) {
            selectedTower = existing
            clearBuildType()
            return true
        }

        val type = selectedType
        if (type == null) {
            selectedTower = null
            return false
        }

        placing = true
        ghostCol = col
        ghostRow = row
        return true
    }

    fun onMapMove(x: Float, y: Float) {
        if (!placing) return
        val (col, row) = gameMap.pixelToCell(x, y)
        ghostCol = col
        ghostRow = row
    }

    /** 手指放開，真正落塔。 */
    fun onMapUp() {
        if (!placing) return
        val type = selectedType
        val col = ghostCol
        val row = ghostRow
        cancelPlacement()
        if (type == null) return

        if (!gameMap.canPlaceTower(col, row)) {
            Audio.play(Sfx.DENY)
            val (cx, cy) = gameMap.centerOf(col.coerceIn(0, GameMap.COLS - 1), row.coerceIn(0, GameMap.ROWS - 1))
            Fx.text(cx, cy, Strings.get(R.string.toast_cannot_build), Colors.of("#FCA5A5"), Ui.dp(11f), 40)
            return
        }
        if (GameState.gold < type.baseCost) {
            Audio.play(Sfx.DENY)
            val (cx, cy) = gameMap.centerOf(col, row)
            Fx.text(cx, cy, Strings.get(R.string.toast_no_gold), Colors.of("#FCA5A5"), Ui.dp(11f), 40)
            return
        }

        GameState.spendGold(type.baseCost)
        val tower = Tower(col, row, type, gameMap)
        towers.add(tower)
        gameMap.occupy(col, row)
        selectedTower = tower
        Audio.play(Sfx.BUILD)
        Fx.ring(tower.centerX, tower.centerY, gameMap.cellSize * 0.7f, Colors.of(type.accentColor), 18)
        Fx.burst(tower.centerX, tower.centerY, 10, Colors.of(type.accentColor), Ui.dp(1.6f), Ui.dp(2f), 22)
    }

    fun cancelPlacement() {
        placing = false
        ghostCol = -1
        ghostRow = -1
    }

    // ---- 升級 / 賣塔 / 選敵模式 ----

    fun canUpgradeSelected(): Boolean {
        val tower = selectedTower ?: return false
        return !tower.isMaxLevel && GameState.gold >= tower.upgradeCost
    }

    fun upgradeSelected(): Boolean {
        val tower = selectedTower ?: return false
        if (tower.isMaxLevel) {
            Audio.play(Sfx.DENY)
            return false
        }
        if (GameState.gold < tower.upgradeCost) {
            Audio.play(Sfx.DENY)
            Fx.text(tower.centerX, tower.centerY, Strings.get(R.string.toast_no_gold), Colors.of("#FCA5A5"), Ui.dp(11f), 40)
            return false
        }
        GameState.spendGold(tower.upgradeCost)
        tower.upgrade()
        Audio.play(Sfx.UPGRADE)
        Fx.ring(tower.centerX, tower.centerY, gameMap.cellSize * 0.9f, Colors.of(tower.type.accentColor), 22)
        Fx.burst(tower.centerX, tower.centerY, 14, Colors.of(tower.type.accentColor), Ui.dp(1.8f), Ui.dp(2.2f), 26, gravity = -0.04f)
        Fx.text(tower.centerX, tower.centerY - gameMap.cellSize * 0.4f, Strings.format(R.string.toast_level_up, tower.level), Colors.of("#FFE08A"), Ui.dp(13f), 44)
        return true
    }

    fun sellSelected(): Boolean {
        val tower = selectedTower ?: return false
        val refund = tower.sellValue
        // 直接加回金幣，不計入 goldEarned，否則反覆蓋塔賣塔可以刷分數
        GameState.gold += refund
        towers.remove(tower)
        gameMap.release(tower.col, tower.row)
        selectedTower = null
        Audio.play(Sfx.SELL)
        Fx.burst(tower.centerX, tower.centerY, 12, Colors.of("#FFD75E"), Ui.dp(1.6f), Ui.dp(2f), 24)
        Fx.goldGain(tower.centerX, tower.centerY, refund)
        return true
    }

    fun cycleTargetModeOfSelected() {
        val tower = selectedTower ?: return
        tower.targetMode = tower.targetMode.next()
        Audio.play(Sfx.BUILD)
    }

    // ---- 更新 ----

    fun update(enemies: List<Enemy>, obstacleManager: ObstacleManager) {
        if (GameState.status != GameStatus.PLAYING) return

        val alive = enemies.filter { it.isAlive }

        for (tower in towers) {
            tower.tick()
            if (tower.cooldown > 0) continue

            if (tower.type.isAreaPulse) {
                if (firePulse(tower, alive, obstacleManager)) tower.onFired()
                continue
            }

            val target = pickTarget(tower, alive)
            if (target != null) {
                aimAt(tower, target.x, target.y)
                when {
                    tower.type == TowerType.LIGHT -> fireChainLightning(tower, target, alive)
                    tower.type.isPiercing -> fireRocket(tower, target)
                    else -> fireProjectile(tower, target)
                }
                tower.onFired()
                continue
            }

            // 射程內沒有敵人時才去清障，避免玩家點一下障礙物就漏怪
            val obstacle = obstacleManager.focusedInRange(tower.centerX, tower.centerY, tower.range)
            if (obstacle != null) {
                aimAt(tower, obstacle.centerX, obstacle.centerY)
                if (tower.type.isPiercing) {
                    fireRocketAt(tower, obstacle.centerX, obstacle.centerY)
                } else {
                    strikeObstacle(tower, obstacle, enemies)
                }
                tower.onFired()
            }
        }

        for (bullet in bullets) bullet.update(enemies)
        bullets.removeAll { it.isDone }
        for (shot in shots) shot.update(enemies, obstacleManager.obstacles)
        shots.removeAll { it.isDone }
    }

    private fun aimAt(tower: Tower, x: Float, y: Float) {
        if (!tower.type.rotatesToTarget) return
        val dx = x - tower.centerX
        val dy = y - tower.centerY
        tower.aimAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    /** 太陽／月亮：以自身為圓心的脈衝，無差別掃到範圍內的敵人與障礙物。 */
    private fun firePulse(tower: Tower, alive: List<Enemy>, obstacleManager: ObstacleManager): Boolean {
        val targets = alive.filter {
            it.isAlive && distance(tower.centerX, tower.centerY, it.x, it.y) <= tower.range
        }
        val props = obstacleManager.inRadius(tower.centerX, tower.centerY, tower.range)
        if (targets.isEmpty() && props.isEmpty()) return false

        val color = Colors.of(tower.type.accentColor)
        Fx.ring(tower.centerX, tower.centerY, tower.range, color, 24, Ui.dp(3.4f))
        for (enemy in targets) {
            enemy.takeDamage(tower.damage, showNumber = false)
            if (tower.type == TowerType.MOON) {
                enemy.applySlow(tower.auraSlowFactor, tower.auraSlowDuration)
            }
        }
        for (prop in props) prop.takeDamage(tower.damage, alive)
        Audio.play(if (tower.type == TowerType.SUN) Sfx.EXPLODE else Sfx.ICE)
        return true
    }

    private fun fireRocket(tower: Tower, target: Enemy) {
        fireRocketAt(tower, target.x, target.y)
    }

    private fun fireRocketAt(tower: Tower, x: Float, y: Float) {
        val dx = x - tower.centerX
        val dy = y - tower.centerY
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        shots.add(
            PiercingShot(
                startX = tower.centerX,
                startY = tower.centerY,
                dirX = dx / dist,
                dirY = dy / dist,
                // 射程只決定「打得到誰」；打出去之後就一路飛到出界，
                // 沿途所有敵人與障礙物都會吃到傷害
                maxDistance = mapDiagonal(),
                damage = tower.damage,
                speed = gameMap.cellSize * 0.2f,
                hitRadius = gameMap.cellSize * 0.36f
            )
        )
        Audio.play(Sfx.SHOOT)
    }

    /** 對角線長度：保證火箭無論往哪個方向打都能飛出畫面。 */
    private fun mapDiagonal(): Float {
        val w = gameMap.cellSize * GameMap.COLS
        val h = gameMap.cellSize * GameMap.ROWS
        return sqrt(w * w + h * h)
    }

    private fun strikeObstacle(tower: Tower, obstacle: Obstacle, enemies: List<Enemy>) {
        Fx.beam(
            tower.centerX, tower.centerY, obstacle.centerX, obstacle.centerY,
            Colors.of(tower.type.accentColor), 8
        )
        obstacle.takeDamage(tower.damage, enemies)
        Audio.play(Sfx.HIT)
    }

    private fun pickTarget(tower: Tower, alive: List<Enemy>): Enemy? {
        var best: Enemy? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (enemy in alive) {
            if (!enemy.isAlive) continue
            val d = distance(tower.centerX, tower.centerY, enemy.x, enemy.y)
            if (d > tower.range) continue
            val score = when (tower.targetMode) {
                TargetMode.FIRST -> enemy.distanceTravelled
                TargetMode.STRONGEST -> enemy.hp.toFloat()
                TargetMode.CLOSEST -> -d
            }
            if (score > bestScore) {
                bestScore = score
                best = enemy
            }
        }
        return best
    }

    private fun fireProjectile(tower: Tower, target: Enemy) {
        val speed = gameMap.cellSize * 0.16f
        val bullet = when (tower.type) {
            TowerType.ARROW -> Bullet(
                tower.centerX, tower.centerY, target, tower.damage, TowerType.ARROW,
                speed = speed * 1.4f
            )
            TowerType.BOMB -> Bullet(
                tower.centerX, tower.centerY, target, tower.damage, TowerType.BOMB,
                speed = speed * 0.9f, splashRadius = tower.splashRadius
            )
            TowerType.ICE -> Bullet(
                tower.centerX, tower.centerY, target, tower.damage, TowerType.ICE,
                speed = speed * 1.1f, slowFactor = tower.slowFactor, slowDuration = tower.slowDuration
            )
            TowerType.POISON -> Bullet(
                tower.centerX, tower.centerY, target, tower.damage, TowerType.POISON,
                speed = speed, poisonDamage = tower.poisonDamage, poisonDuration = tower.poisonDuration
            )
            TowerType.LIGHT, TowerType.MOON, TowerType.ROCKET, TowerType.SUN -> return
        }
        bullets.add(bullet)
        Audio.play(Sfx.SHOOT)
    }

    /** 電塔：瞬發，從主目標往鄰近敵人跳躍。 */
    private fun fireChainLightning(tower: Tower, first: Enemy, alive: List<Enemy>) {
        val hit = ArrayList<Enemy>()
        val chainRange = gameMap.cellSize * 1.9f
        var current = first
        var fromX = tower.centerX
        var fromY = tower.centerY
        val color = Colors.of("#C4B5FD")

        var remaining = tower.chainTargets
        while (remaining > 0) {
            hit.add(current)
            Fx.beam(fromX, fromY, current.x, current.y, color, 9)
            Fx.hitSpark(current.x, current.y, color)
            current.takeDamage(tower.damage)
            fromX = current.x
            fromY = current.y
            remaining--
            if (remaining == 0) break

            var next: Enemy? = null
            var nextDist = Float.MAX_VALUE
            for (enemy in alive) {
                if (!enemy.isAlive || hit.contains(enemy)) continue
                val d = distance(fromX, fromY, enemy.x, enemy.y)
                if (d <= chainRange && d < nextDist) {
                    nextDist = d
                    next = enemy
                }
            }
            current = next ?: break
        }
        Audio.play(Sfx.ZAP)
    }

    // ---- 存檔 ----

    fun snapshot(): List<TowerSnapshot> = towers.map {
        TowerSnapshot(
            col = it.col,
            row = it.row,
            type = it.type.name,
            level = it.level,
            cooldown = it.cooldown,
            aimAngle = it.aimAngle,
            invested = it.invested,
            targetMode = it.targetMode.name
        )
    }

    fun restore(snapshots: List<TowerSnapshot>) {
        reset()
        for (snapshot in snapshots) {
            if (!gameMap.isValidCell(snapshot.col, snapshot.row)) continue
            if (!gameMap.canPlaceTower(snapshot.col, snapshot.row)) continue
            val tower = Tower(snapshot.col, snapshot.row, TowerType.fromName(snapshot.type), gameMap).also {
                it.level = snapshot.level.coerceIn(1, Tower.MAX_LEVEL)
                it.cooldown = snapshot.cooldown
                it.aimAngle = snapshot.aimAngle
                it.invested = snapshot.invested
                it.targetMode = TargetMode.fromName(snapshot.targetMode)
            }
            towers.add(tower)
            gameMap.occupy(snapshot.col, snapshot.row)
        }
    }

    // ---- 繪製 ----

    fun draw(canvas: Canvas) {
        drawSelectionRange(canvas)
        drawGhost(canvas)
        for (tower in towers) tower.draw(canvas)
        for (bullet in bullets) bullet.draw(canvas)
        for (shot in shots) shot.draw(canvas)
    }

    private fun drawSelectionRange(canvas: Canvas) {
        val tower = selectedTower ?: return
        paint.style = Paint.Style.FILL
        paint.color = Colors.of("#14FFE08A")
        canvas.drawCircle(tower.centerX, tower.centerY, tower.range, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = Ui.dp(1.6f)
        paint.color = Colors.of("#99FFE08A")
        canvas.drawCircle(tower.centerX, tower.centerY, tower.range, paint)

        // 選中框
        val cs = gameMap.cellSize
        val px = gameMap.offsetX + tower.col * cs
        val py = gameMap.offsetY + tower.row * cs
        paint.strokeWidth = Ui.dp(2f)
        paint.color = Colors.of("#CCFFF3C4")
        scratch.set(px + cs * 0.04f, py + cs * 0.04f, px + cs * 0.96f, py + cs * 0.96f)
        canvas.drawRoundRect(scratch, cs * 0.2f, cs * 0.2f, paint)
    }

    private fun drawGhost(canvas: Canvas) {
        if (!ghostVisible) return
        val type = selectedType ?: return
        val cs = gameMap.cellSize
        val px = gameMap.offsetX + ghostCol * cs
        val py = gameMap.offsetY + ghostRow * cs
        val (cx, cy) = gameMap.centerOf(ghostCol, ghostRow)
        val valid = gameMap.canPlaceTower(ghostCol, ghostRow) && GameState.gold >= type.baseCost
        val tint = if (valid) "#5EE07A" else "#F87171"

        val range = cs * type.rangeCells(1)

        paint.style = Paint.Style.FILL
        paint.color = Colors.of(if (valid) "#1A5EE07A" else "#1AF87171")
        canvas.drawCircle(cx, cy, range, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = Ui.dp(1.6f)
        paint.color = Colors.of(tint)
        canvas.drawCircle(cx, cy, range, paint)

        paint.style = Paint.Style.FILL
        paint.color = Colors.of(if (valid) "#665EE07A" else "#66F87171")
        scratch.set(px + cs * 0.06f, py + cs * 0.06f, px + cs * 0.94f, py + cs * 0.94f)
        canvas.drawRoundRect(scratch, cs * 0.2f, cs * 0.2f, paint)

        paint.alpha = 190
        paint.textSize = cs * 0.5f
        val emoji = type.emoji
        canvas.drawText(emoji, cx - paint.measureText(emoji) / 2f, cy + cs * 0.18f, paint)
        paint.alpha = 255
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}
