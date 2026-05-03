package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.*

class TowerManager(private val gameMap: GameMap) {

    val towers = mutableListOf<Tower>()
    val bullets = mutableListOf<Bullet>()

    // 當前選中要放置的塔類型（null = 未選擇）
    var selectedType: TowerType? = null
    var selectedTower: Tower? = null
        private set

    private val paint = Paint().apply { isAntiAlias = true }

    fun reset() {
        towers.clear()
        bullets.clear()
        selectedType = null
        selectedTower = null
    }

    fun toggleBuildType(type: TowerType) {
        selectedType = if (selectedType == type) null else type
        selectedTower = null
    }

    fun update(enemies: List<Enemy>) {
        if (GameState.status != GameStatus.PLAYING) return

        // 塔攻擊邏輯
        for (tower in towers) {
            if (tower.cooldown > 0) {
                tower.cooldown--
                continue
            }
            // 找範圍內血量最多的敵人（保衛蘿蔔原版策略：打最前面的）
            val target = enemies
                .filter { !it.isDead && !it.hasReachedEnd }
                .filter { dist(tower.centerX, tower.centerY, it.x, it.y) <= tower.range }
                .maxByOrNull { it.distanceTravelled }   // 最靠近終點的
                ?: continue

            // 更新箭塔朝向
            if (tower.type == TowerType.ARROW) {
                val dx = target.x - tower.centerX
                val dy = target.y - tower.centerY
                tower.aimAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            }

            // 發射子彈
            val bullet = when (tower.type) {
                TowerType.ARROW -> Bullet(
                    tower.centerX, tower.centerY, target,
                    damage = tower.damage, type = TowerType.ARROW
                )
                TowerType.BOMB -> Bullet(
                    tower.centerX, tower.centerY, target,
                    damage = tower.damage, type = TowerType.BOMB,
                    splashRadius = gameMap.cellSize * 1.5f
                )
                TowerType.ICE -> Bullet(
                    tower.centerX, tower.centerY, target,
                    damage = tower.damage, type = TowerType.ICE,
                    slowFactor = 0.4f, slowDuration = 90
                )
            }
            bullets.add(bullet)
            tower.cooldown = tower.attackInterval
        }

        // 更新子彈
        for (bullet in bullets) bullet.update(enemies)
        bullets.removeAll { it.isDone }
    }

    // 點擊格子：放塔或升級
    fun onTap(pixelX: Float, pixelY: Float) {
        val (col, row) = gameMap.pixelToCell(pixelX, pixelY)
        if (!gameMap.isValidCell(col, row)) return

        // 已有塔 → 選中，交給升級面板處理
        val existing = towers.find { it.col == col && it.row == row }
        if (existing != null) {
            selectedTower = existing
            selectedType = null
            return
        }

        // 空地 → 放塔
        val type = selectedType ?: return
        if (!gameMap.canPlaceTower(col, row)) return
        val cost = when (type) {
            TowerType.ARROW -> 50
            TowerType.BOMB  -> 80
            TowerType.ICE   -> 60
        }
        if (GameState.gold < cost) return
        GameState.gold -= cost
        val tower = Tower(col, row, type, gameMap)
        towers.add(tower)
        selectedTower = tower
        gameMap.grid[row][col] = GameMap.BLOCKED
    }

    fun canUpgradeSelected(): Boolean {
        val tower = selectedTower ?: return false
        return tower.level < 3 && GameState.gold >= tower.upgradeCost
    }

    fun upgradeSelected(): Boolean {
        val tower = selectedTower ?: return false
        if (!canUpgradeSelected()) return false
        GameState.gold -= tower.upgradeCost
        tower.level++
        return true
    }

    fun draw(canvas: Canvas) {
        selectedTower?.let { tower ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.parseColor("#88FFDD55")
            canvas.drawCircle(tower.centerX, tower.centerY, tower.range, paint)
        }
        for (tower in towers) tower.draw(canvas)
        for (bullet in bullets) bullet.draw(canvas)
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
        sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
}
