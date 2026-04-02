package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas

class EnemyManager(private val gameMap: GameMap) {

    val enemies = mutableListOf<Enemy>()

    // 生成間隔：每60幀生成一個（約1秒）
    private var spawnTimer = 0
    private val spawnInterval = 60
    private var spawnedCount = 0
    private val totalEnemies = 10   // 第一波10個

    fun update() {
        if (GameState.status != GameStatus.PLAYING) return

        // 生成敵人
        if (spawnedCount < totalEnemies) {
            spawnTimer++
            if (spawnTimer >= spawnInterval) {
                spawnTimer = 0
                spawnedCount++
                enemies.add(Enemy(gameMap))
            }
        }

        // 更新所有敵人
        for (enemy in enemies) {
            enemy.update()
        }

        // 清理死亡或已到終點的敵人
        enemies.removeAll { it.isDead || it.hasReachedEnd }

        // 判斷勝利：全部生成完且全部清除
        if (spawnedCount >= totalEnemies && enemies.isEmpty()
            && GameState.status == GameStatus.PLAYING) {
            GameState.status = GameStatus.VICTORY
        }
    }

    fun draw(canvas: Canvas) {
        for (enemy in enemies) {
            enemy.draw(canvas)
        }
    }
}