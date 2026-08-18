package io.github.acidefluorhydrique.carrot

/**
 * 一波中的一組敵人。hp / speed / reward 是「基礎值」，
 * 最終數值再乘上 [EnemyKind] 的倍率。
 */
data class WaveGroup(
    val kind: EnemyKind,
    val count: Int,
    val hp: Int,
    val speed: Float,
    val reward: Int,
    val spawnInterval: Int
) {
    val actualHp: Int get() = (hp * kind.hpMultiplier).toInt().coerceAtLeast(1)
    val actualSpeed: Float get() = speed * kind.speedMultiplier
    val actualReward: Int get() = (reward * kind.rewardMultiplier).toInt().coerceAtLeast(1)
}

data class WaveConfig(
    val groups: List<WaveGroup>,
    /** 本波清空後到下一波的緩衝影格數。 */
    val restFrames: Int = 110
) {
    val totalCount: Int get() = groups.sumOf { it.count }

    /** 波次預覽用：出現的種類（去重、保留順序）。 */
    val kinds: List<EnemyKind> get() = groups.map { it.kind }.distinct()

    fun hasBoss(): Boolean = groups.any { it.kind.isBoss }
}

data class LevelConfig(
    val id: Int,
    val nameRes: Int,
    val subtitleRes: Int,
    val startGold: Int,
    val carrotHp: Int,
    val path: List<Pair<Int, Int>>,
    /** 不可蓋塔的裝飾地形（石頭、水塘）。 */
    val blocked: List<Pair<Int, Int>> = emptyList(),
    val waves: List<WaveConfig>
) {
    val name: String get() = Strings.get(nameRes)
    val subtitle: String get() = Strings.get(subtitleRes)
}

object GameLevels {

    val all: List<LevelConfig> = listOf(
        LevelConfig(
            id = 1,
            nameRes = R.string.level_1_name,
            subtitleRes = R.string.level_1_desc,
            startGold = 160,
            carrotHp = 12,
            path = listOf(
                0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0, 6 to 0,
                6 to 1,
                6 to 2, 7 to 2, 8 to 2, 9 to 2, 10 to 2, 11 to 2, 12 to 2, 13 to 2,
                13 to 3,
                13 to 4, 14 to 4, 15 to 4
            ),
            blocked = listOf(2 to 4, 3 to 4, 9 to 5, 10 to 5),
            waves = listOf(
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 8, 5, 1.6f, 12, 55)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 10, 8, 1.7f, 13, 48)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.SWARM, 14, 12, 1.7f, 16, 22),
                    WaveGroup(EnemyKind.GRUNT, 4, 12, 1.7f, 15, 50)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.RUNNER, 8, 15, 1.7f, 16, 34),
                    WaveGroup(EnemyKind.GRUNT, 6, 16, 1.75f, 17, 46)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 8, 18, 1.8f, 17, 40),
                    WaveGroup(EnemyKind.TANK, 3, 20, 1.5f, 24, 78)
                ))
            )
        ),
        LevelConfig(
            id = 2,
            nameRes = R.string.level_2_name,
            subtitleRes = R.string.level_2_desc,
            startGold = 175,
            carrotHp = 11,
            path = listOf(
                0 to 5, 1 to 5, 2 to 5, 3 to 5,
                3 to 4, 3 to 3, 3 to 2,
                4 to 2, 5 to 2, 6 to 2, 7 to 2,
                7 to 3, 7 to 4, 7 to 5, 7 to 6,
                8 to 6, 9 to 6, 10 to 6, 11 to 6, 12 to 6,
                12 to 5, 12 to 4, 12 to 3,
                13 to 3, 14 to 3, 15 to 3
            ),
            blocked = listOf(0 to 0, 1 to 0, 9 to 0, 14 to 7, 15 to 7),
            waves = listOf(
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 10, 8, 1.7f, 13, 48)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.SWARM, 16, 14, 1.75f, 16, 20)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.RUNNER, 10, 18, 1.75f, 17, 32),
                    WaveGroup(EnemyKind.GRUNT, 6, 20, 1.8f, 18, 44)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 8, 22, 1.8f, 19, 40),
                    WaveGroup(EnemyKind.TANK, 4, 24, 1.5f, 26, 70)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.SWARM, 20, 20, 1.8f, 18, 18),
                    WaveGroup(EnemyKind.RUNNER, 10, 24, 1.8f, 19, 30)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 10, 30, 1.85f, 22, 38),
                    WaveGroup(EnemyKind.TANK, 5, 32, 1.5f, 30, 62)
                ))
            )
        ),
        LevelConfig(
            id = 3,
            nameRes = R.string.level_3_name,
            subtitleRes = R.string.level_3_desc,
            startGold = 195,
            carrotHp = 10,
            path = listOf(
                0 to 7, 1 to 7, 2 to 7, 3 to 7, 4 to 7,
                4 to 6, 4 to 5, 4 to 4,
                5 to 4, 6 to 4, 7 to 4, 8 to 4,
                8 to 3, 8 to 2, 8 to 1,
                9 to 1, 10 to 1, 11 to 1, 12 to 1,
                12 to 2, 12 to 3, 12 to 4, 12 to 5,
                13 to 5, 14 to 5, 15 to 5
            ),
            blocked = listOf(0 to 0, 1 to 0, 0 to 3, 1 to 3, 14 to 0, 15 to 0),
            waves = listOf(
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 12, 10, 1.75f, 14, 42)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.RUNNER, 12, 16, 1.8f, 16, 30)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.SWARM, 20, 20, 1.8f, 18, 18),
                    WaveGroup(EnemyKind.GRUNT, 8, 24, 1.8f, 19, 40)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.RUNNER, 10, 26, 1.85f, 20, 28),
                    WaveGroup(EnemyKind.TANK, 5, 30, 1.55f, 30, 64)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.SWARM, 22, 26, 1.85f, 19, 16),
                    WaveGroup(EnemyKind.GRUNT, 14, 34, 1.85f, 22, 36)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.RUNNER, 12, 32, 1.9f, 22, 26),
                    WaveGroup(EnemyKind.TANK, 6, 40, 1.6f, 34, 58)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 10, 36, 1.85f, 22, 34),
                    WaveGroup(EnemyKind.BOSS, 1, 180, 1.5f, 60, 90)
                ), restFrames = 150)
            )
        ),
        LevelConfig(
            id = 4,
            nameRes = R.string.level_4_name,
            subtitleRes = R.string.level_4_desc,
            startGold = 210,
            carrotHp = 10,
            path = listOf(
                0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1, 7 to 1,
                7 to 2, 7 to 3,
                6 to 3, 5 to 3, 4 to 3, 3 to 3, 2 to 3,
                2 to 4, 2 to 5,
                3 to 5, 4 to 5, 5 to 5, 6 to 5, 7 to 5, 8 to 5, 9 to 5,
                9 to 4, 9 to 3, 9 to 2,
                10 to 2, 11 to 2, 12 to 2,
                12 to 3, 12 to 4, 12 to 5, 12 to 6,
                13 to 6, 14 to 6, 15 to 6
            ),
            blocked = listOf(0 to 6, 1 to 6, 0 to 7, 5 to 7, 14 to 0, 15 to 0),
            waves = listOf(
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 12, 14, 1.8f, 15, 40)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.SWARM, 22, 22, 1.85f, 18, 16)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.RUNNER, 14, 26, 1.85f, 19, 26),
                    WaveGroup(EnemyKind.GRUNT, 8, 30, 1.85f, 20, 38)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.TANK, 6, 36, 1.55f, 32, 60),
                    WaveGroup(EnemyKind.SWARM, 20, 30, 1.9f, 20, 16)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.RUNNER, 16, 34, 1.9f, 22, 24),
                    WaveGroup(EnemyKind.GRUNT, 12, 42, 1.9f, 24, 34)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.TANK, 8, 46, 1.6f, 36, 54),
                    WaveGroup(EnemyKind.RUNNER, 14, 40, 1.95f, 24, 24)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.SWARM, 28, 40, 1.95f, 22, 14),
                    WaveGroup(EnemyKind.GRUNT, 14, 52, 1.9f, 26, 32)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.TANK, 6, 55, 1.6f, 40, 52),
                    WaveGroup(EnemyKind.BOSS, 1, 260, 1.5f, 70, 90)
                ), restFrames = 150)
            )
        ),
        LevelConfig(
            id = 5,
            nameRes = R.string.level_5_name,
            subtitleRes = R.string.level_5_desc,
            startGold = 240,
            carrotHp = 9,
            path = listOf(
                0 to 3, 1 to 3, 2 to 3,
                2 to 2, 2 to 1,
                3 to 1, 4 to 1, 5 to 1, 6 to 1,
                6 to 2, 6 to 3, 6 to 4, 6 to 5,
                5 to 5, 4 to 5,
                4 to 6, 4 to 7,
                5 to 7, 6 to 7, 7 to 7, 8 to 7, 9 to 7,
                9 to 6, 9 to 5, 9 to 4,
                10 to 4, 11 to 4,
                11 to 3, 11 to 2, 11 to 1,
                12 to 1, 13 to 1,
                13 to 2, 13 to 3, 13 to 4, 13 to 5,
                14 to 5, 15 to 5
            ),
            blocked = listOf(0 to 0, 1 to 0, 0 to 6, 0 to 7, 2 to 6, 15 to 0, 15 to 7),
            waves = listOf(
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 14, 18, 1.85f, 16, 36)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.RUNNER, 16, 26, 1.9f, 19, 24)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.SWARM, 26, 30, 1.9f, 20, 14),
                    WaveGroup(EnemyKind.TANK, 5, 40, 1.6f, 34, 58)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 14, 48, 1.9f, 26, 32),
                    WaveGroup(EnemyKind.RUNNER, 14, 42, 1.95f, 24, 24)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.TANK, 8, 55, 1.6f, 40, 52),
                    WaveGroup(EnemyKind.SWARM, 28, 44, 1.95f, 24, 14)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.GRUNT, 16, 62, 1.95f, 30, 30),
                    WaveGroup(EnemyKind.BOSS, 1, 300, 1.5f, 80, 90)
                ), restFrames = 150),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.RUNNER, 18, 56, 2.0f, 28, 22),
                    WaveGroup(EnemyKind.TANK, 10, 70, 1.65f, 44, 48)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.SWARM, 34, 56, 2.0f, 26, 12),
                    WaveGroup(EnemyKind.GRUNT, 18, 78, 1.95f, 32, 28)
                )),
                WaveConfig(listOf(
                    WaveGroup(EnemyKind.TANK, 8, 85, 1.65f, 48, 46),
                    WaveGroup(EnemyKind.BOSS, 2, 420, 1.5f, 110, 180)
                ), restFrames = 180)
            )
        )
    )

    val default: LevelConfig = all.first()

    fun byId(id: Int): LevelConfig = all.firstOrNull { it.id == id } ?: default

    /** 第一關永遠開放，之後每關需要通過前一關。 */
    fun isUnlocked(levelId: Int, completed: Set<Int>): Boolean {
        val index = all.indexOfFirst { it.id == levelId }
        if (index <= 0) return true
        return all[index - 1].id in completed
    }

    fun nextLevel(levelId: Int): LevelConfig? {
        val index = all.indexOfFirst { it.id == levelId }
        if (index < 0 || index >= all.size - 1) return null
        return all[index + 1]
    }
}
