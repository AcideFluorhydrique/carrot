// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import kotlin.math.sqrt

/**
 * 場上的可摧毀障礙物。
 *
 * 集火規則：可以同時標記多個，塔只有在「射程內沒有敵人」時才會去打，
 * 否則點錯一下就會漏怪，變成懲罰玩家的設計。
 * 射程內有好幾個標記時照標記順序打：順序全場共用又不會變，
 * 所以同一座塔不會在兩個目標之間來回切，夠得到的塔也會自然集中火力。
 */
class ObstacleManager(private val gameMap: GameMap) {

    val obstacles = mutableListOf<Obstacle>()

    /** 依標記先後排列，第一個優先。 */
    private val marked = mutableListOf<Obstacle>()

    fun reset(level: LevelConfig) {
        obstacles.clear()
        marked.clear()
        val hpScale = obstacleHpScale(level)
        val goldScale = obstacleGoldScale(level)
        for (spec in level.obstacles) {
            if (!gameMap.isValidCell(spec.col, spec.row)) continue
            obstacles.add(
                Obstacle(
                    col = spec.col,
                    row = spec.row,
                    kind = spec.kind,
                    maxHp = (hpScale * spec.kind.hpFactor).toInt().coerceAtLeast(8),
                    goldReward = (goldScale * spec.kind.goldFactor).toInt().coerceAtLeast(3),
                    gameMap = gameMap
                )
            )
        }
    }

    /** 點擊障礙物格：標記（排到最後）或取消標記。回傳 true 表示這次點擊被吃掉。 */
    fun onTap(col: Int, row: Int): Boolean {
        val target = obstacles.firstOrNull { it.isAlive && it.col == col && it.row == row } ?: return false
        if (target.markOrder > 0) {
            target.markOrder = 0
            marked.remove(target)
            renumberMarks()
            Audio.play(Sfx.SELL)
        } else {
            marked.add(target)
            target.markOrder = marked.size
            Audio.play(Sfx.BUILD)
        }
        return true
    }

    fun update() {
        for (obstacle in obstacles) obstacle.tick()
        if (marked.removeAll { !it.isAlive }) renumberMarks()
        obstacles.removeAll { it.isDestroyed }
    }

    /** 前面的標記被打掉或取消後，後面的往前補，畫面上的序號才會從 1 開始連續。 */
    private fun renumberMarks() {
        marked.forEachIndexed { index, obstacle -> obstacle.markOrder = index + 1 }
    }

    fun aliveAt(col: Int, row: Int): Obstacle? =
        obstacles.firstOrNull { it.isAlive && it.col == col && it.row == row }

    /** 射程內最早標記的目標，供「閒置時清障」使用。 */
    fun markedInRange(x: Float, y: Float, range: Float): Obstacle? =
        marked.firstOrNull {
            if (!it.isAlive) return@firstOrNull false
            val dx = it.centerX - x
            val dy = it.centerY - y
            sqrt(dx * dx + dy * dy) <= range
        }

    /** 範圍型武器會無差別掃到障礙物，不需要玩家指定。 */
    fun inRadius(x: Float, y: Float, radius: Float): List<Obstacle> =
        obstacles.filter {
            if (!it.isAlive) return@filter false
            val dx = it.centerX - x
            val dy = it.centerY - y
            dx * dx + dy * dy <= radius * radius
        }

    fun draw(canvas: Canvas) {
        for (obstacle in obstacles) obstacle.draw(canvas)
    }

    // ---- 存檔 ----

    fun snapshot(): List<ObstacleSnapshot> =
        obstacles.filter { it.isAlive }.map { ObstacleSnapshot(it.col, it.row, it.kind.name, it.hp) }

    fun restore(level: LevelConfig, snapshots: List<ObstacleSnapshot>) {
        reset(level)
        val remaining = snapshots.associateBy { it.col to it.row }
        val iterator = obstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()
            val saved = remaining[obstacle.col to obstacle.row]
            if (saved == null) {
                // 存檔時已經被打掉了
                gameMap.clearObstacle(obstacle.col, obstacle.row)
                iterator.remove()
            } else {
                obstacle.restoreHp(saved.hp)
            }
        }
    }

    companion object {
        /** 障礙物血量相對於該關第一波的敵人血量。 */
        fun obstacleHpScale(level: LevelConfig): Float {
            val firstWaveHp = level.waves.firstOrNull()?.groups?.firstOrNull()?.hp ?: 5
            return firstWaveHp * 12f
        }

        /**
         * 障礙物血量跟著敵人血量指數成長，但敵人獎勵是線性的，
         * 直接用敵人獎勵當基準的話，後期清障會愈來愈不划算，所以額外補一個成長項。
         */
        fun obstacleGoldScale(level: LevelConfig): Float {
            val firstWaveReward = level.waves.firstOrNull()?.groups?.firstOrNull()?.reward ?: 12
            return firstWaveReward * (1f + level.id * 0.12f)
        }
    }
}
