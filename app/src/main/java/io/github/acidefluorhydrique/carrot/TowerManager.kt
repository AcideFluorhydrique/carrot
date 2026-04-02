package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import kotlin.math.*

class TowerManager(private val gameMap: GameMap) {

    val towers = mutableListOf<Tower>()
    val bullets = mutableListOf<Bullet>()

    // 當前選中要放置的塔類型（null = 未選擇）
    var selectedType: TowerType? = null

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
                .maxByOrNull { pathProgress(it) }   // 最靠近終點的
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

        // 已有塔 → 嘗試升級
        val existing = towers.find { it.col == col && it.row == row }
        if (existing != null) {
            if (existing.level < 3 && GameState.gold >= existing.upgradeCost) {
                GameState.gold -= existing.upgradeCost
                existing.level++
            }
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
        towers.add(Tower(col, row, type, gameMap))
        gameMap.grid[row][col] = GameMap.BLOCKED
    }

    fun draw(canvas: Canvas) {
        for (tower in towers) tower.draw(canvas)
        for (bullet in bullets) bullet.draw(canvas)
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
        sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))

    private fun pathProgress(enemy: Enemy): Float {
        // 用敵人位置到起點的距離估算進度
        val (startCol, startRow) = gameMap.pathPoints[0]
        val (startPx, startPy) = gameMap.cellToPixel(startCol, startRow)
        return dist(startPx, startPy, enemy.x, enemy.y)
    }
}