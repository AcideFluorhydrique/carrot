package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas

class EnemyManager(private val gameMap: GameMap) {

    val enemies = mutableListOf<Enemy>()

    private var level = GameLevels.default
    private var spawnTimer = 0
    private var spawnedInWave = 0
    private var waveIndex = 0
    private var interWaveTimer = 80

    fun reset(selectedLevel: LevelConfig) {
        level = selectedLevel
        enemies.clear()
        spawnTimer = 0
        spawnedInWave = 0
        waveIndex = 0
        interWaveTimer = 80
        GameState.wave = 1
    }

    fun snapshot(): EnemyManagerSnapshot {
        return EnemyManagerSnapshot(
            spawnTimer = spawnTimer,
            spawnedInWave = spawnedInWave,
            waveIndex = waveIndex,
            interWaveTimer = interWaveTimer,
            enemies = enemies
                .filter { !it.isDead && !it.hasReachedEnd }
                .map { it.snapshot() }
        )
    }

    fun restore(selectedLevel: LevelConfig, snapshot: EnemyManagerSnapshot) {
        level = selectedLevel
        spawnTimer = snapshot.spawnTimer
        spawnedInWave = snapshot.spawnedInWave
        waveIndex = snapshot.waveIndex
        interWaveTimer = snapshot.interWaveTimer
        enemies.clear()
        for (enemySnapshot in snapshot.enemies) {
            enemies.add(Enemy(gameMap, level.waves[waveIndex.coerceIn(0, level.waves.lastIndex)]).also {
                it.restore(enemySnapshot)
            })
        }
        GameState.wave = (waveIndex + 1).coerceAtMost(level.waves.size)
    }

    fun update() {
        if (GameState.status != GameStatus.PLAYING) return

        if (waveIndex < level.waves.size) {
            GameState.wave = waveIndex + 1
            val wave = level.waves[waveIndex]

            if (spawnedInWave < wave.count) {
                spawnTimer++
                if (spawnTimer >= wave.spawnInterval) {
                    spawnTimer = 0
                    spawnedInWave++
                    enemies.add(Enemy(gameMap, wave))
                }
            } else if (enemies.isEmpty()) {
                interWaveTimer--
                if (interWaveTimer <= 0) {
                    waveIndex++
                    spawnedInWave = 0
                    spawnTimer = 0
                    interWaveTimer = 80
                }
            }
        }

        // 更新所有敵人
        for (enemy in enemies) {
            enemy.update()
        }

        // 清理死亡或已到終點的敵人
        enemies.removeAll { it.isDead || it.hasReachedEnd }

        // 判斷勝利：全部生成完且全部清除
        if (waveIndex >= level.waves.size && enemies.isEmpty()
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
