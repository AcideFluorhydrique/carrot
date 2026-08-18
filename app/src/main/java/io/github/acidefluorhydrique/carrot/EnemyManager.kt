package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas

class EnemyManager(private val gameMap: GameMap) {

    companion object {
        const val PHASE_PREPARING = 0
        const val PHASE_SPAWNING = 1
        const val PHASE_RESTING = 2
        const val PHASE_DONE = 3

        private const val PREP_FRAMES = 240
    }

    val enemies = mutableListOf<Enemy>()

    private var level = GameLevels.default
    private var phase = PHASE_PREPARING
    private var waveIndex = 0
    private var groupIndex = 0
    private var spawnedInGroup = 0
    private var spawnTimer = 0
    private var restTimer = PREP_FRAMES

    val currentWaveNumber: Int get() = (waveIndex + 1).coerceAtMost(level.waves.size)
    val totalWaves: Int get() = level.waves.size
    val isPreparing: Boolean get() = phase == PHASE_PREPARING
    val isResting: Boolean get() = phase == PHASE_RESTING
    val countdownSeconds: Int get() = (restTimer / 60) + 1

    /** 目前這波尚未生成的敵人數。 */
    val pendingInWave: Int
        get() {
            if (phase != PHASE_SPAWNING && phase != PHASE_PREPARING) return 0
            val wave = level.waves.getOrNull(waveIndex) ?: return 0
            var pending = 0
            for (i in wave.groups.indices) {
                val group = wave.groups[i]
                pending += when {
                    i < groupIndex -> 0
                    i == groupIndex && phase == PHASE_SPAWNING -> group.count - spawnedInGroup
                    else -> group.count
                }
            }
            return pending
        }

    /** 提前叫下一波可拿的獎勵金。 */
    val callBonus: Int
        get() = if (canCallNextWave()) 20 + currentWaveNumber * 6 + restTimer / 8 else 0

    fun canCallNextWave(): Boolean {
        if (GameState.status != GameStatus.PLAYING) return false
        return phase == PHASE_PREPARING || phase == PHASE_RESTING
    }

    /** 玩家主動催下一波，換取獎勵金。 */
    fun callNextWave() {
        if (!canCallNextWave()) return
        val bonus = callBonus
        GameState.addGold(bonus)
        val (cx, cy) = if (gameMap.pathPoints.isNotEmpty()) {
            gameMap.centerOf(gameMap.pathPoints.first().first, gameMap.pathPoints.first().second)
        } else Pair(0f, 0f)
        Fx.goldGain(cx, cy - gameMap.cellSize * 0.5f, bonus)
        Fx.ring(cx, cy, gameMap.cellSize * 1.1f, android.graphics.Color.parseColor("#FFD75E"), 20)
        if (phase == PHASE_PREPARING) {
            beginWave(0)
        } else {
            advanceWave()
        }
    }

    fun reset(selectedLevel: LevelConfig) {
        level = selectedLevel
        enemies.clear()
        phase = PHASE_PREPARING
        waveIndex = 0
        groupIndex = 0
        spawnedInGroup = 0
        spawnTimer = 0
        restTimer = PREP_FRAMES
        GameState.wave = 1
    }

    fun update() {
        if (GameState.status != GameStatus.PLAYING) return

        when (phase) {
            PHASE_PREPARING -> {
                restTimer--
                if (restTimer <= 0) beginWave(0)
            }
            PHASE_SPAWNING -> updateSpawning()
            PHASE_RESTING -> {
                if (enemies.none { it.isAlive }) {
                    restTimer--
                    if (restTimer <= 0) advanceWave()
                }
            }
            PHASE_DONE -> Unit
        }

        for (enemy in enemies) enemy.update()
        enemies.removeAll { it.isDead || it.hasReachedEnd }

        if (phase == PHASE_DONE && enemies.isEmpty() && GameState.status == GameStatus.PLAYING) {
            GameState.status = GameStatus.VICTORY
        }
    }

    private fun updateSpawning() {
        val wave = level.waves.getOrNull(waveIndex)
        if (wave == null) {
            phase = PHASE_DONE
            return
        }
        if (groupIndex >= wave.groups.size) {
            phase = if (waveIndex >= level.waves.size - 1) PHASE_DONE else PHASE_RESTING
            restTimer = wave.restFrames
            return
        }

        val group = wave.groups[groupIndex]
        spawnTimer++
        if (spawnTimer >= group.spawnInterval) {
            spawnTimer = 0
            spawn(group)
            spawnedInGroup++
            if (spawnedInGroup >= group.count) {
                groupIndex++
                spawnedInGroup = 0
                spawnTimer = group.spawnInterval / 2
            }
        }
    }

    private fun spawn(group: WaveGroup) {
        enemies.add(
            Enemy(
                gameMap,
                group.kind,
                group.actualHp,
                group.actualSpeed,
                group.actualReward
            )
        )
    }

    private fun beginWave(index: Int) {
        waveIndex = index
        groupIndex = 0
        spawnedInGroup = 0
        spawnTimer = Int.MAX_VALUE / 2   // 立刻生成第一隻
        phase = PHASE_SPAWNING
        GameState.wave = currentWaveNumber
        Audio.play(Sfx.WAVE)
        val wave = level.waves.getOrNull(index)
        if (wave != null && wave.hasBoss()) {
            Fx.shake(Ui.dp(3f), 24)
        }
    }

    private fun advanceWave() {
        if (waveIndex >= level.waves.size - 1) {
            phase = PHASE_DONE
            return
        }
        beginWave(waveIndex + 1)
    }

    /** HUD 波次預覽用。 */
    fun currentWave(): WaveConfig? = level.waves.getOrNull(waveIndex)

    fun nextWavePreview(): WaveConfig? = when (phase) {
        PHASE_PREPARING -> level.waves.getOrNull(0)
        PHASE_RESTING -> level.waves.getOrNull(waveIndex + 1)
        else -> null
    }

    /** 場上血量最多的首領，給 HUD 畫大血條。 */
    fun activeBoss(): Enemy? = enemies
        .filter { it.isAlive && it.kind.isBoss }
        .maxByOrNull { it.hp }

    // ---- 存檔 ----

    fun snapshot(): EnemyManagerSnapshot = EnemyManagerSnapshot(
        phase = phase,
        waveIndex = waveIndex,
        groupIndex = groupIndex,
        spawnedInGroup = spawnedInGroup,
        spawnTimer = spawnTimer.coerceAtMost(100000),
        restTimer = restTimer,
        enemies = enemies.filter { it.isAlive }.map { it.snapshot() }
    )

    fun restore(selectedLevel: LevelConfig, snapshot: EnemyManagerSnapshot) {
        level = selectedLevel
        enemies.clear()
        phase = snapshot.phase.coerceIn(PHASE_PREPARING, PHASE_DONE)
        waveIndex = snapshot.waveIndex.coerceIn(0, (level.waves.size - 1).coerceAtLeast(0))
        groupIndex = snapshot.groupIndex.coerceAtLeast(0)
        spawnedInGroup = snapshot.spawnedInGroup.coerceAtLeast(0)
        spawnTimer = snapshot.spawnTimer.coerceAtLeast(0)
        restTimer = snapshot.restTimer.coerceAtLeast(0)
        for (enemySnapshot in snapshot.enemies) {
            val kind = EnemyKind.fromName(enemySnapshot.kind)
            val enemy = Enemy(gameMap, kind, enemySnapshot.maxHp, enemySnapshot.baseSpeed, enemySnapshot.goldReward)
            enemy.restore(enemySnapshot)
            enemies.add(enemy)
        }
        GameState.wave = currentWaveNumber
    }

    fun draw(canvas: Canvas) {
        for (enemy in enemies) enemy.draw(canvas)
    }
}
