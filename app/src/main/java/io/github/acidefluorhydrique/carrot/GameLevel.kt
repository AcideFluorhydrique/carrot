// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

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

    fun hasSwarm(): Boolean = groups.any { it.kind == EnemyKind.SWARM }
}

data class LevelConfig(
    val id: Int,
    val chapterId: Int,
    /** 章節內的第幾關，1 起算。 */
    val indexInChapter: Int,
    val startGold: Int,
    val carrotHp: Int,
    val path: List<Pair<Int, Int>>,
    /** 永久地形：定義地圖形狀，打不掉也蓋不了。 */
    val permanent: List<Pair<Int, Int>>,
    /** 可摧毀的障礙物，清掉可以拿錢並空出建塔位。 */
    val obstacles: List<ObstacleSpec>,
    /** 這一關開放使用的防禦塔，還要再和解鎖進度取交集。 */
    val allowedTowers: List<TowerType>,
    val waves: List<WaveConfig>
) {
    val chapter: Chapter get() = Chapters.byId(chapterId)

    /** 「第 3 關」。 */
    val displayName: String get() = Strings.format(R.string.level_number, indexInChapter)

    /** 「夜森 3-2」。 */
    val fullName: String
        get() = Strings.format(R.string.level_full_name, chapter.name, chapterId, indexInChapter)

    fun hasBoss(): Boolean = waves.any { it.hasBoss() }
}

/**
 * 20 個關卡，分成 5 章。
 * 難度曲線與敵人登場時機是算出來的：每種新敵人都緊跟在剋制它的塔解鎖之後，
 * 而每章的第一關會給額外起始金並縮短波數，形成「喘一口氣再往上爬」的節奏。
 * 每關只開放 4~5 座塔，讓同樣的一批工具能組出不一樣的題目。
 */
object GameLevels {

    val all: List<LevelConfig> = listOf(
        LevelConfig(
            id = 1,
            chapterId = 1,
            indexInChapter = 1,
            startGold = 150,
            carrotHp = 14,
            path = listOf(
                0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2,
                7 to 3, 7 to 4, 7 to 5, 8 to 5, 9 to 5, 10 to 5, 11 to 5, 12 to 5,
                13 to 5, 14 to 5, 15 to 5
            ),
            permanent = listOf(0 to 1, 0 to 3, 15 to 4, 15 to 6),
            obstacles = listOf(
                ObstacleSpec(8, 2, ObstacleKind.ROCK),
                ObstacleSpec(2, 3, ObstacleKind.TREE),
                ObstacleSpec(13, 6, ObstacleKind.MUSHROOM)
            ),
            allowedTowers = listOf(TowerType.ARROW),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 9, 5, 1.60f, 13, 51)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 6, 1.60f, 14, 51)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 11, 6, 1.60f, 15, 51)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 12, 7, 1.60f, 16, 51)
                    )
                )
            )
        ),
        LevelConfig(
            id = 2,
            chapterId = 1,
            indexInChapter = 2,
            startGold = 157,
            carrotHp = 13,
            path = listOf(
                0 to 5, 1 to 5, 2 to 5, 3 to 5, 4 to 5, 4 to 4, 4 to 3, 4 to 2,
                5 to 2, 6 to 2, 7 to 2, 8 to 2, 9 to 2, 10 to 2, 11 to 2, 11 to 3,
                11 to 4, 11 to 5, 12 to 5, 13 to 5, 14 to 5, 15 to 5
            ),
            permanent = listOf(0 to 4, 15 to 4, 0 to 6, 15 to 6),
            obstacles = listOf(
                ObstacleSpec(11, 0, ObstacleKind.TREE),
                ObstacleSpec(5, 1, ObstacleKind.MUSHROOM),
                ObstacleSpec(10, 5, ObstacleKind.ROCK)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 9, 6, 1.62f, 14, 51)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 7, 1.62f, 15, 51)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 11, 7, 1.62f, 16, 51)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 12, 8, 1.62f, 17, 51)
                    )
                )
            )
        ),
        LevelConfig(
            id = 3,
            chapterId = 1,
            indexInChapter = 3,
            startGold = 164,
            carrotHp = 12,
            path = listOf(
                0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 5 to 2, 5 to 3,
                5 to 4, 5 to 5, 6 to 5, 7 to 5, 8 to 5, 9 to 5, 10 to 5, 10 to 4,
                10 to 3, 10 to 2, 11 to 2, 12 to 2, 13 to 2, 14 to 2, 15 to 2
            ),
            permanent = listOf(0 to 0, 15 to 1, 0 to 2, 15 to 3),
            obstacles = listOf(
                ObstacleSpec(2, 0, ObstacleKind.CRATE),
                ObstacleSpec(13, 3, ObstacleKind.ROCK),
                ObstacleSpec(7, 4, ObstacleKind.TREE)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 7, 1.64f, 15, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 11, 8, 1.64f, 16, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 12, 9, 1.64f, 17, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 13, 10, 1.64f, 18, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 14, 10, 1.64f, 19, 50)
                    )
                )
            )
        ),
        LevelConfig(
            id = 4,
            chapterId = 1,
            indexInChapter = 4,
            startGold = 171,
            carrotHp = 12,
            path = listOf(
                0 to 6, 1 to 6, 2 to 6, 3 to 6, 3 to 5, 3 to 4, 3 to 3, 3 to 2,
                4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 2, 8 to 3, 8 to 4, 8 to 5,
                8 to 6, 9 to 6, 10 to 6, 11 to 6, 12 to 6, 12 to 5, 12 to 4, 12 to 3,
                13 to 3, 14 to 3, 15 to 3
            ),
            permanent = listOf(15 to 2, 15 to 4, 0 to 5, 0 to 7),
            obstacles = listOf(
                ObstacleSpec(10, 2, ObstacleKind.CRATE),
                ObstacleSpec(4, 3, ObstacleKind.TREE),
                ObstacleSpec(9, 7, ObstacleKind.MUSHROOM)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 9, 1.66f, 16, 49)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 11, 10, 1.66f, 17, 49)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 12, 11, 1.66f, 18, 49)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 13, 11, 1.66f, 19, 49)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 14, 12, 1.66f, 20, 49)
                    )
                )
            )
        ),
        LevelConfig(
            id = 5,
            chapterId = 2,
            indexInChapter = 1,
            startGold = 238,
            carrotHp = 12,
            path = listOf(
                0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0, 5 to 1, 5 to 2,
                5 to 3, 5 to 4, 6 to 4, 7 to 4, 8 to 4, 9 to 4, 9 to 3, 9 to 2,
                9 to 1, 10 to 1, 11 to 1, 12 to 1, 13 to 1, 13 to 2, 13 to 3, 13 to 4,
                13 to 5, 14 to 5, 15 to 5
            ),
            permanent = listOf(0 to 1, 15 to 4, 15 to 6),
            obstacles = listOf(
                ObstacleSpec(13, 0, ObstacleKind.MUSHROOM),
                ObstacleSpec(7, 1, ObstacleKind.ROCK),
                ObstacleSpec(1, 2, ObstacleKind.TREE),
                ObstacleSpec(12, 5, ObstacleKind.CRATE)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.BOMB),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 10, 1.69f, 18, 48)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 21, 12, 1.69f, 19, 16),
                        WaveGroup(EnemyKind.GRUNT, 5, 14, 1.69f, 19, 54)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 12, 13, 1.69f, 19, 32),
                        WaveGroup(EnemyKind.GRUNT, 6, 16, 1.69f, 19, 52)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 15, 16, 1.69f, 20, 42)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 14, 15, 1.69f, 21, 48)
                    )
                )
            )
        ),
        LevelConfig(
            id = 6,
            chapterId = 2,
            indexInChapter = 2,
            startGold = 185,
            carrotHp = 12,
            path = listOf(
                0 to 4, 1 to 4, 2 to 4, 3 to 4, 3 to 3, 3 to 2, 3 to 1, 4 to 1,
                5 to 1, 6 to 1, 7 to 1, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5,
                8 to 6, 9 to 6, 10 to 6, 11 to 6, 12 to 6, 12 to 5, 12 to 4, 12 to 3,
                12 to 2, 13 to 2, 14 to 2, 15 to 2
            ),
            permanent = listOf(15 to 1, 0 to 3, 15 to 3, 0 to 5),
            obstacles = listOf(
                ObstacleSpec(4, 0, ObstacleKind.ROCK),
                ObstacleSpec(11, 3, ObstacleKind.TREE),
                ObstacleSpec(9, 4, ObstacleKind.CRATE),
                ObstacleSpec(3, 5, ObstacleKind.ROCK)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.BOMB),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 11, 12, 1.71f, 19, 48)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 22, 14, 1.71f, 20, 16),
                        WaveGroup(EnemyKind.GRUNT, 5, 16, 1.71f, 20, 54)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 13, 15, 1.71f, 21, 32),
                        WaveGroup(EnemyKind.GRUNT, 6, 18, 1.71f, 21, 52)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 16, 20, 1.71f, 22, 42)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 15, 18, 1.71f, 22, 48)
                    )
                )
            )
        ),
        LevelConfig(
            id = 7,
            chapterId = 2,
            indexInChapter = 3,
            startGold = 192,
            carrotHp = 11,
            path = listOf(
                0 to 7, 1 to 7, 2 to 7, 3 to 7, 3 to 6, 3 to 5, 3 to 4, 3 to 3,
                4 to 3, 5 to 3, 6 to 3, 6 to 4, 6 to 5, 6 to 6, 7 to 6, 8 to 6,
                9 to 6, 10 to 6, 10 to 5, 10 to 4, 10 to 3, 10 to 2, 10 to 1, 11 to 1,
                12 to 1, 13 to 1, 13 to 2, 13 to 3, 13 to 4, 13 to 5, 14 to 5, 15 to 5
            ),
            permanent = listOf(15 to 4, 0 to 6, 15 to 6),
            obstacles = listOf(
                ObstacleSpec(14, 1, ObstacleKind.TREE),
                ObstacleSpec(8, 2, ObstacleKind.CRATE),
                ObstacleSpec(12, 2, ObstacleKind.ROCK),
                ObstacleSpec(11, 7, ObstacleKind.TREE)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.BOMB, TowerType.MOON),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 11, 15, 1.73f, 20, 47)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 23, 17, 1.73f, 21, 15),
                        WaveGroup(EnemyKind.GRUNT, 5, 20, 1.73f, 21, 53)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 13, 18, 1.73f, 22, 31),
                        WaveGroup(EnemyKind.GRUNT, 6, 22, 1.73f, 22, 51)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 16, 23, 1.73f, 23, 41)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 15, 21, 1.73f, 24, 47)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 31, 23, 1.73f, 24, 15),
                        WaveGroup(EnemyKind.GRUNT, 5, 26, 1.73f, 24, 53)
                    )
                )
            )
        ),
        LevelConfig(
            id = 8,
            chapterId = 2,
            indexInChapter = 4,
            startGold = 199,
            carrotHp = 11,
            path = listOf(
                0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 4 to 3, 4 to 4, 4 to 5,
                4 to 6, 5 to 6, 6 to 6, 7 to 6, 7 to 5, 7 to 4, 7 to 3, 7 to 2,
                7 to 1, 8 to 1, 9 to 1, 10 to 1, 11 to 1, 11 to 2, 11 to 3, 11 to 4,
                11 to 5, 12 to 5, 13 to 5, 14 to 5, 14 to 4, 14 to 3, 14 to 2, 15 to 2
            ),
            permanent = listOf(0 to 1, 15 to 1, 0 to 3, 15 to 3),
            obstacles = listOf(
                ObstacleSpec(11, 0, ObstacleKind.CRATE),
                ObstacleSpec(15, 0, ObstacleKind.ROCK),
                ObstacleSpec(8, 6, ObstacleKind.TREE),
                ObstacleSpec(2, 7, ObstacleKind.MUSHROOM)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.BOMB, TowerType.MOON),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 11, 18, 1.75f, 21, 46)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 24, 20, 1.75f, 22, 15),
                        WaveGroup(EnemyKind.GRUNT, 6, 23, 1.75f, 22, 52)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 14, 22, 1.75f, 23, 30),
                        WaveGroup(EnemyKind.GRUNT, 7, 26, 1.75f, 23, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 16, 28, 1.75f, 24, 40)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 15, 26, 1.75f, 25, 46)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 32, 28, 1.75f, 26, 15),
                        WaveGroup(EnemyKind.GRUNT, 6, 32, 1.75f, 26, 52)
                    )
                )
            )
        ),
        LevelConfig(
            id = 9,
            chapterId = 3,
            indexInChapter = 1,
            startGold = 266,
            carrotHp = 11,
            path = listOf(
                0 to 3, 1 to 3, 2 to 3, 3 to 3, 3 to 4, 3 to 5, 3 to 6, 3 to 7,
                4 to 7, 5 to 7, 6 to 7, 6 to 6, 6 to 5, 6 to 4, 6 to 3, 6 to 2,
                7 to 2, 8 to 2, 9 to 2, 10 to 2, 10 to 3, 10 to 4, 10 to 5, 10 to 6,
                11 to 6, 12 to 6, 13 to 6, 13 to 5, 13 to 4, 13 to 3, 13 to 2, 13 to 1,
                14 to 1, 15 to 1
            ),
            permanent = listOf(15 to 0, 0 to 2, 15 to 2, 0 to 4),
            obstacles = listOf(
                ObstacleSpec(6, 0, ObstacleKind.TOXIC),
                ObstacleSpec(0, 1, ObstacleKind.TREE),
                ObstacleSpec(7, 4, ObstacleKind.CRATE),
                ObstacleSpec(11, 4, ObstacleKind.TOXIC),
                ObstacleSpec(5, 5, ObstacleKind.TREE)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.BOMB, TowerType.MOON, TowerType.POISON),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 12, 21, 1.77f, 22, 46)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 25, 24, 1.77f, 23, 15),
                        WaveGroup(EnemyKind.GRUNT, 6, 28, 1.77f, 23, 52)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 14, 26, 1.77f, 24, 30),
                        WaveGroup(EnemyKind.GRUNT, 7, 31, 1.77f, 24, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 8, 29, 1.77f, 25, 46),
                        WaveGroup(EnemyKind.TANK, 4, 36, 1.77f, 38, 64)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 16, 31, 1.77f, 26, 46)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 33, 33, 1.77f, 27, 15),
                        WaveGroup(EnemyKind.GRUNT, 6, 38, 1.77f, 27, 52),
                        WaveGroup(EnemyKind.TANK, 4, 40, 1.77f, 40, 62)
                    )
                )
            )
        ),
        LevelConfig(
            id = 10,
            chapterId = 3,
            indexInChapter = 2,
            startGold = 213,
            carrotHp = 10,
            path = listOf(
                0 to 6, 1 to 6, 2 to 6, 2 to 5, 2 to 4, 2 to 3, 2 to 2, 2 to 1,
                3 to 1, 4 to 1, 5 to 1, 5 to 2, 5 to 3, 5 to 4, 5 to 5, 6 to 5,
                7 to 5, 8 to 5, 9 to 5, 9 to 4, 9 to 3, 9 to 2, 9 to 1, 9 to 0,
                10 to 0, 11 to 0, 12 to 0, 12 to 1, 12 to 2, 12 to 3, 12 to 4, 13 to 4,
                14 to 4, 15 to 4
            ),
            permanent = listOf(15 to 3, 0 to 5, 15 to 5, 0 to 7),
            obstacles = listOf(
                ObstacleSpec(10, 2, ObstacleKind.CRATE),
                ObstacleSpec(14, 2, ObstacleKind.ROCK),
                ObstacleSpec(4, 3, ObstacleKind.TOXIC),
                ObstacleSpec(8, 3, ObstacleKind.TREE),
                ObstacleSpec(15, 6, ObstacleKind.ROCK)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.BOMB, TowerType.MOON, TowerType.POISON),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 12, 26, 1.79f, 23, 45)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 26, 29, 1.79f, 24, 15),
                        WaveGroup(EnemyKind.GRUNT, 6, 33, 1.79f, 24, 51)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 15, 31, 1.79f, 25, 29),
                        WaveGroup(EnemyKind.GRUNT, 7, 37, 1.79f, 25, 49)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 8, 34, 1.79f, 26, 45),
                        WaveGroup(EnemyKind.TANK, 5, 42, 1.79f, 39, 63)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 16, 37, 1.79f, 27, 45)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 34, 40, 1.79f, 28, 15),
                        WaveGroup(EnemyKind.GRUNT, 6, 46, 1.79f, 28, 51),
                        WaveGroup(EnemyKind.TANK, 4, 48, 1.79f, 42, 61)
                    )
                )
            )
        ),
        LevelConfig(
            id = 11,
            chapterId = 3,
            indexInChapter = 3,
            startGold = 220,
            carrotHp = 10,
            path = listOf(
                0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2, 2 to 3, 2 to 4, 2 to 5,
                3 to 5, 4 to 5, 5 to 5, 5 to 4, 5 to 3, 5 to 2, 5 to 1, 6 to 1,
                7 to 1, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 8 to 6, 9 to 6,
                10 to 6, 11 to 6, 11 to 5, 11 to 4, 11 to 3, 11 to 2, 12 to 2, 13 to 2,
                14 to 2, 14 to 3, 14 to 4, 14 to 5, 14 to 6, 14 to 7, 15 to 7
            ),
            permanent = listOf(0 to 1, 15 to 6),
            obstacles = listOf(
                ObstacleSpec(13, 0, ObstacleKind.ROCK),
                ObstacleSpec(11, 1, ObstacleKind.TOXIC),
                ObstacleSpec(1, 2, ObstacleKind.TREE),
                ObstacleSpec(12, 5, ObstacleKind.CRATE),
                ObstacleSpec(4, 7, ObstacleKind.TOXIC)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.BOMB, TowerType.MOON, TowerType.POISON, TowerType.ROCKET),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 12, 31, 1.81f, 25, 44)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 27, 34, 1.81f, 25, 14),
                        WaveGroup(EnemyKind.GRUNT, 6, 39, 1.81f, 25, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 15, 38, 1.81f, 26, 28),
                        WaveGroup(EnemyKind.GRUNT, 7, 46, 1.81f, 26, 48)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 8, 41, 1.81f, 27, 44),
                        WaveGroup(EnemyKind.TANK, 5, 51, 1.81f, 40, 62)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 16, 45, 1.81f, 28, 44)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 35, 48, 1.81f, 29, 14),
                        WaveGroup(EnemyKind.GRUNT, 6, 55, 1.81f, 29, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 19, 51, 1.81f, 30, 28),
                        WaveGroup(EnemyKind.GRUNT, 7, 61, 1.81f, 30, 48),
                        WaveGroup(EnemyKind.TANK, 4, 61, 1.81f, 45, 60)
                    )
                )
            )
        ),
        LevelConfig(
            id = 12,
            chapterId = 3,
            indexInChapter = 4,
            startGold = 227,
            carrotHp = 9,
            path = listOf(
                0 to 7, 1 to 7, 2 to 7, 2 to 6, 2 to 5, 2 to 4, 2 to 3, 2 to 2,
                3 to 2, 4 to 2, 5 to 2, 5 to 3, 5 to 4, 5 to 5, 5 to 6, 6 to 6,
                7 to 6, 8 to 6, 8 to 5, 8 to 4, 8 to 3, 8 to 2, 8 to 1, 9 to 1,
                10 to 1, 11 to 1, 11 to 2, 11 to 3, 11 to 4, 12 to 4, 13 to 4, 13 to 3,
                13 to 2, 13 to 1, 13 to 0, 14 to 0, 15 to 0
            ),
            permanent = listOf(15 to 1, 0 to 6),
            obstacles = listOf(
                ObstacleSpec(4, 0, ObstacleKind.TOXIC),
                ObstacleSpec(8, 0, ObstacleKind.CRATE),
                ObstacleSpec(2, 1, ObstacleKind.ROCK),
                ObstacleSpec(7, 5, ObstacleKind.TOXIC),
                ObstacleSpec(1, 6, ObstacleKind.TREE)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.BOMB, TowerType.MOON, TowerType.POISON, TowerType.ROCKET),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 13, 37, 1.83f, 26, 44)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 28, 41, 1.83f, 27, 14),
                        WaveGroup(EnemyKind.GRUNT, 7, 47, 1.83f, 27, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 16, 45, 1.83f, 27, 28),
                        WaveGroup(EnemyKind.GRUNT, 8, 54, 1.83f, 27, 48)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 9, 49, 1.83f, 28, 44),
                        WaveGroup(EnemyKind.TANK, 5, 61, 1.83f, 42, 62)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 17, 53, 1.83f, 29, 44)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 36, 58, 1.83f, 30, 14),
                        WaveGroup(EnemyKind.GRUNT, 7, 67, 1.83f, 30, 50)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 20, 62, 1.83f, 31, 28),
                        WaveGroup(EnemyKind.GRUNT, 8, 74, 1.83f, 31, 48),
                        WaveGroup(EnemyKind.BOSS, 1, 112, 1.83f, 93, 150)
                    ), restFrames = 150
                )
            )
        ),
        LevelConfig(
            id = 13,
            chapterId = 4,
            indexInChapter = 1,
            startGold = 294,
            carrotHp = 10,
            path = listOf(
                0 to 4, 1 to 4, 2 to 4, 2 to 3, 2 to 2, 2 to 1, 2 to 0, 3 to 0,
                4 to 0, 5 to 0, 5 to 1, 5 to 2, 5 to 3, 5 to 4, 5 to 5, 6 to 5,
                7 to 5, 8 to 5, 8 to 4, 8 to 3, 8 to 2, 8 to 1, 9 to 1, 10 to 1,
                11 to 1, 11 to 2, 11 to 3, 11 to 4, 11 to 5, 11 to 6, 12 to 6, 13 to 6,
                14 to 6, 14 to 5, 14 to 4, 14 to 3, 14 to 2, 15 to 2
            ),
            permanent = listOf(15 to 1, 0 to 3, 15 to 3, 0 to 5),
            obstacles = listOf(
                ObstacleSpec(12, 2, ObstacleKind.ROCK),
                ObstacleSpec(6, 3, ObstacleKind.MUSHROOM),
                ObstacleSpec(10, 3, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(4, 4, ObstacleKind.CRATE),
                ObstacleSpec(11, 7, ObstacleKind.MUSHROOM),
                ObstacleSpec(15, 7, ObstacleKind.ICE_BLOCK)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.POISON, TowerType.ROCKET, TowerType.LIGHT),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 13, 45, 1.85f, 27, 43)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 29, 49, 1.85f, 28, 14),
                        WaveGroup(EnemyKind.GRUNT, 7, 56, 1.85f, 28, 49)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 16, 54, 1.85f, 29, 27),
                        WaveGroup(EnemyKind.GRUNT, 8, 65, 1.85f, 29, 47)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 9, 59, 1.85f, 30, 43),
                        WaveGroup(EnemyKind.TANK, 5, 74, 1.85f, 45, 61)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 17, 64, 1.85f, 30, 43)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 37, 69, 1.85f, 31, 14),
                        WaveGroup(EnemyKind.GRUNT, 7, 79, 1.85f, 31, 49)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 20, 74, 1.85f, 32, 27),
                        WaveGroup(EnemyKind.GRUNT, 8, 89, 1.85f, 32, 47),
                        WaveGroup(EnemyKind.TANK, 5, 89, 1.85f, 48, 59)
                    )
                )
            )
        ),
        LevelConfig(
            id = 14,
            chapterId = 4,
            indexInChapter = 2,
            startGold = 241,
            carrotHp = 9,
            path = listOf(
                0 to 1, 1 to 1, 2 to 1, 3 to 1, 3 to 2, 3 to 3, 3 to 4, 3 to 5,
                3 to 6, 4 to 6, 5 to 6, 6 to 6, 6 to 5, 6 to 4, 6 to 3, 6 to 2,
                6 to 1, 6 to 0, 7 to 0, 8 to 0, 9 to 0, 9 to 1, 9 to 2, 9 to 3,
                9 to 4, 9 to 5, 10 to 5, 11 to 5, 12 to 5, 12 to 4, 12 to 3, 12 to 2,
                12 to 1, 13 to 1, 14 to 1, 14 to 2, 14 to 3, 14 to 4, 14 to 5, 14 to 6,
                15 to 6
            ),
            permanent = listOf(0 to 0, 0 to 2, 15 to 5, 15 to 7),
            obstacles = listOf(
                ObstacleSpec(15, 0, ObstacleKind.CRATE),
                ObstacleSpec(7, 2, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(1, 3, ObstacleKind.ROCK),
                ObstacleSpec(8, 6, ObstacleKind.MUSHROOM),
                ObstacleSpec(12, 6, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(6, 7, ObstacleKind.ROCK)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.BOMB, TowerType.MOON, TowerType.POISON),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 13, 53, 1.87f, 28, 42)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 30, 59, 1.87f, 29, 14),
                        WaveGroup(EnemyKind.GRUNT, 7, 68, 1.87f, 29, 48)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 17, 65, 1.87f, 30, 26),
                        WaveGroup(EnemyKind.GRUNT, 8, 78, 1.87f, 30, 46)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 9, 71, 1.87f, 31, 42),
                        WaveGroup(EnemyKind.TANK, 5, 89, 1.87f, 46, 60)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 17, 77, 1.87f, 32, 42)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 38, 83, 1.87f, 32, 14),
                        WaveGroup(EnemyKind.GRUNT, 7, 95, 1.87f, 32, 48)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 21, 89, 1.87f, 33, 26),
                        WaveGroup(EnemyKind.GRUNT, 8, 107, 1.87f, 33, 46),
                        WaveGroup(EnemyKind.TANK, 5, 107, 1.87f, 50, 58)
                    )
                )
            )
        ),
        LevelConfig(
            id = 15,
            chapterId = 4,
            indexInChapter = 3,
            startGold = 248,
            carrotHp = 9,
            path = listOf(
                0 to 5, 1 to 5, 2 to 5, 2 to 4, 2 to 3, 2 to 2, 2 to 1, 2 to 0,
                3 to 0, 4 to 0, 4 to 1, 4 to 2, 4 to 3, 4 to 4, 4 to 5, 4 to 6,
                5 to 6, 6 to 6, 7 to 6, 7 to 5, 7 to 4, 7 to 3, 7 to 2, 8 to 2,
                9 to 2, 10 to 2, 10 to 3, 10 to 4, 10 to 5, 10 to 6, 10 to 7, 11 to 7,
                12 to 7, 12 to 6, 12 to 5, 12 to 4, 12 to 3, 13 to 3, 14 to 3, 14 to 4,
                14 to 5, 14 to 6, 15 to 6
            ),
            permanent = listOf(0 to 4, 15 to 5, 0 to 6, 15 to 7),
            obstacles = listOf(
                ObstacleSpec(6, 0, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(10, 0, ObstacleKind.ROCK),
                ObstacleSpec(0, 1, ObstacleKind.MUSHROOM),
                ObstacleSpec(15, 4, ObstacleKind.CRATE),
                ObstacleSpec(9, 5, ObstacleKind.ROCK),
                ObstacleSpec(3, 6, ObstacleKind.MUSHROOM)
            ),
            allowedTowers = listOf(TowerType.MOON, TowerType.POISON, TowerType.ROCKET, TowerType.LIGHT, TowerType.SUN),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 14, 64, 1.90f, 29, 42)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 31, 71, 1.90f, 30, 14),
                        WaveGroup(EnemyKind.GRUNT, 7, 82, 1.90f, 30, 48)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 17, 78, 1.90f, 31, 26),
                        WaveGroup(EnemyKind.GRUNT, 8, 94, 1.90f, 31, 46)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 9, 85, 1.90f, 32, 42),
                        WaveGroup(EnemyKind.TANK, 6, 106, 1.90f, 48, 60)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 18, 92, 1.90f, 33, 42)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 38, 100, 1.90f, 34, 14),
                        WaveGroup(EnemyKind.GRUNT, 7, 115, 1.90f, 34, 48)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 21, 107, 1.90f, 35, 26),
                        WaveGroup(EnemyKind.GRUNT, 8, 128, 1.90f, 35, 46)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 9, 114, 1.90f, 35, 42),
                        WaveGroup(EnemyKind.TANK, 6, 142, 1.90f, 52, 60)
                    )
                )
            )
        ),
        LevelConfig(
            id = 16,
            chapterId = 4,
            indexInChapter = 4,
            startGold = 255,
            carrotHp = 8,
            path = listOf(
                0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2, 2 to 3, 2 to 4, 2 to 5,
                2 to 6, 3 to 6, 4 to 6, 5 to 6, 5 to 5, 5 to 4, 5 to 3, 5 to 2,
                5 to 1, 6 to 1, 7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5, 7 to 6,
                7 to 7, 8 to 7, 9 to 7, 10 to 7, 10 to 6, 10 to 5, 10 to 4, 10 to 3,
                10 to 2, 11 to 2, 12 to 2, 12 to 3, 12 to 4, 12 to 5, 12 to 6, 13 to 6,
                14 to 6, 14 to 5, 14 to 4, 14 to 3, 14 to 2, 14 to 1, 15 to 1
            ),
            permanent = listOf(15 to 0, 0 to 1, 15 to 2),
            obstacles = listOf(
                ObstacleSpec(4, 3, ObstacleKind.CRATE),
                ObstacleSpec(8, 3, ObstacleKind.MUSHROOM),
                ObstacleSpec(6, 4, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(0, 5, ObstacleKind.ROCK),
                ObstacleSpec(15, 6, ObstacleKind.MUSHROOM),
                ObstacleSpec(13, 7, ObstacleKind.ICE_BLOCK)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.BOMB, TowerType.LIGHT, TowerType.SUN),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 14, 77, 1.92f, 30, 41)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 32, 86, 1.92f, 31, 13),
                        WaveGroup(EnemyKind.GRUNT, 8, 99, 1.92f, 31, 47)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 18, 94, 1.92f, 32, 25),
                        WaveGroup(EnemyKind.GRUNT, 9, 113, 1.92f, 32, 45)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 102, 1.92f, 33, 41),
                        WaveGroup(EnemyKind.TANK, 6, 128, 1.92f, 50, 59)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 18, 111, 1.92f, 34, 41)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 38, 119, 1.92f, 35, 13),
                        WaveGroup(EnemyKind.GRUNT, 8, 137, 1.92f, 35, 47)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 22, 128, 1.92f, 36, 25),
                        WaveGroup(EnemyKind.GRUNT, 9, 154, 1.92f, 36, 45)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 136, 1.92f, 37, 41),
                        WaveGroup(EnemyKind.TANK, 6, 170, 1.92f, 56, 59),
                        WaveGroup(EnemyKind.BOSS, 1, 245, 1.92f, 111, 150)
                    ), restFrames = 150
                )
            )
        ),
        LevelConfig(
            id = 17,
            chapterId = 5,
            indexInChapter = 1,
            startGold = 322,
            carrotHp = 9,
            path = listOf(
                0 to 3, 1 to 3, 2 to 3, 2 to 4, 2 to 5, 2 to 6, 2 to 7, 3 to 7,
                4 to 7, 4 to 6, 4 to 5, 4 to 4, 4 to 3, 4 to 2, 4 to 1, 4 to 0,
                5 to 0, 6 to 0, 7 to 0, 7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5,
                8 to 5, 9 to 5, 10 to 5, 10 to 4, 10 to 3, 10 to 2, 10 to 1, 11 to 1,
                12 to 1, 12 to 2, 12 to 3, 12 to 4, 12 to 5, 12 to 6, 13 to 6, 14 to 6,
                14 to 5, 14 to 4, 14 to 3, 14 to 2, 15 to 2
            ),
            permanent = listOf(15 to 1, 0 to 2, 15 to 3, 0 to 4),
            obstacles = listOf(
                ObstacleSpec(13, 0, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(1, 2, ObstacleKind.ROCK),
                ObstacleSpec(5, 2, ObstacleKind.CRATE),
                ObstacleSpec(9, 2, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(3, 3, ObstacleKind.ROCK),
                ObstacleSpec(10, 6, ObstacleKind.TOXIC),
                ObstacleSpec(8, 7, ObstacleKind.ICE_BLOCK)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.MOON, TowerType.POISON, TowerType.ROCKET, TowerType.LIGHT),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 14, 92, 1.94f, 31, 40)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 33, 103, 1.94f, 32, 13),
                        WaveGroup(EnemyKind.GRUNT, 8, 118, 1.94f, 32, 46)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 18, 113, 1.94f, 33, 24),
                        WaveGroup(EnemyKind.GRUNT, 9, 136, 1.94f, 33, 44)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 123, 1.94f, 34, 40),
                        WaveGroup(EnemyKind.TANK, 6, 154, 1.94f, 51, 58)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 18, 133, 1.94f, 35, 40)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 38, 143, 1.94f, 36, 13),
                        WaveGroup(EnemyKind.GRUNT, 8, 164, 1.94f, 36, 46)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 22, 153, 1.94f, 37, 24),
                        WaveGroup(EnemyKind.GRUNT, 9, 184, 1.94f, 37, 44)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 164, 1.94f, 38, 40),
                        WaveGroup(EnemyKind.TANK, 6, 205, 1.94f, 57, 58)
                    )
                )
            )
        ),
        LevelConfig(
            id = 18,
            chapterId = 5,
            indexInChapter = 2,
            startGold = 269,
            carrotHp = 8,
            path = listOf(
                0 to 7, 1 to 7, 2 to 7, 2 to 6, 2 to 5, 2 to 4, 2 to 3, 2 to 2,
                2 to 1, 3 to 1, 4 to 1, 4 to 2, 4 to 3, 4 to 4, 4 to 5, 4 to 6,
                5 to 6, 6 to 6, 7 to 6, 7 to 5, 7 to 4, 7 to 3, 7 to 2, 7 to 1,
                7 to 0, 8 to 0, 9 to 0, 9 to 1, 9 to 2, 9 to 3, 9 to 4, 10 to 4,
                11 to 4, 12 to 4, 12 to 3, 12 to 2, 12 to 1, 12 to 0, 13 to 0, 14 to 0,
                14 to 1, 14 to 2, 14 to 3, 14 to 4, 14 to 5, 15 to 5
            ),
            permanent = listOf(15 to 4, 0 to 6, 15 to 6),
            obstacles = listOf(
                ObstacleSpec(4, 0, ObstacleKind.ROCK),
                ObstacleSpec(6, 1, ObstacleKind.TOXIC),
                ObstacleSpec(0, 2, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(15, 3, ObstacleKind.ROCK),
                ObstacleSpec(13, 4, ObstacleKind.TOXIC),
                ObstacleSpec(11, 5, ObstacleKind.CRATE),
                ObstacleSpec(1, 6, ObstacleKind.ROCK)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.ICE, TowerType.BOMB, TowerType.LIGHT, TowerType.SUN),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 15, 111, 1.96f, 33, 39)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 34, 123, 1.96f, 34, 13),
                        WaveGroup(EnemyKind.GRUNT, 8, 141, 1.96f, 34, 45)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 19, 135, 1.96f, 34, 23),
                        WaveGroup(EnemyKind.GRUNT, 9, 162, 1.96f, 34, 43)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 148, 1.96f, 35, 39),
                        WaveGroup(EnemyKind.TANK, 6, 185, 1.96f, 52, 57)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 19, 160, 1.96f, 36, 39)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 38, 172, 1.96f, 37, 13),
                        WaveGroup(EnemyKind.GRUNT, 8, 198, 1.96f, 37, 45)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 23, 184, 1.96f, 38, 23),
                        WaveGroup(EnemyKind.GRUNT, 9, 221, 1.96f, 38, 43)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 196, 1.96f, 39, 39),
                        WaveGroup(EnemyKind.TANK, 6, 245, 1.96f, 58, 57),
                        WaveGroup(EnemyKind.BOSS, 1, 353, 1.96f, 117, 150)
                    ), restFrames = 150
                )
            )
        ),
        LevelConfig(
            id = 19,
            chapterId = 5,
            indexInChapter = 3,
            startGold = 276,
            carrotHp = 8,
            path = listOf(
                0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2, 2 to 3, 2 to 4, 2 to 5,
                3 to 5, 4 to 5, 4 to 4, 4 to 3, 4 to 2, 4 to 1, 4 to 0, 5 to 0,
                6 to 0, 6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5, 6 to 6, 7 to 6,
                8 to 6, 9 to 6, 9 to 5, 9 to 4, 9 to 3, 9 to 2, 9 to 1, 10 to 1,
                11 to 1, 11 to 2, 11 to 3, 11 to 4, 11 to 5, 11 to 6, 11 to 7, 12 to 7,
                13 to 7, 14 to 7, 14 to 6, 14 to 5, 14 to 4, 14 to 3, 14 to 2, 15 to 2
            ),
            permanent = listOf(0 to 1, 15 to 1, 15 to 3),
            obstacles = listOf(
                ObstacleSpec(3, 0, ObstacleKind.TOXIC),
                ObstacleSpec(14, 1, ObstacleKind.CRATE),
                ObstacleSpec(12, 2, ObstacleKind.ROCK),
                ObstacleSpec(10, 3, ObstacleKind.TOXIC),
                ObstacleSpec(0, 4, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(8, 4, ObstacleKind.ROCK),
                ObstacleSpec(15, 7, ObstacleKind.TOXIC)
            ),
            allowedTowers = listOf(TowerType.ICE, TowerType.BOMB, TowerType.MOON, TowerType.POISON, TowerType.ROCKET),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 15, 133, 1.98f, 34, 39)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 35, 148, 1.98f, 35, 13),
                        WaveGroup(EnemyKind.GRUNT, 8, 170, 1.98f, 35, 45)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 19, 162, 1.98f, 36, 23),
                        WaveGroup(EnemyKind.GRUNT, 9, 194, 1.98f, 36, 43)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 177, 1.98f, 36, 39),
                        WaveGroup(EnemyKind.TANK, 6, 221, 1.98f, 54, 57)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 19, 192, 1.98f, 37, 39)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 38, 206, 1.98f, 38, 13),
                        WaveGroup(EnemyKind.GRUNT, 8, 237, 1.98f, 38, 45)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 23, 221, 1.98f, 39, 23),
                        WaveGroup(EnemyKind.GRUNT, 9, 265, 1.98f, 39, 43)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 10, 236, 1.98f, 40, 39),
                        WaveGroup(EnemyKind.TANK, 6, 295, 1.98f, 60, 57)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 23, 250, 1.98f, 41, 39),
                        WaveGroup(EnemyKind.TANK, 6, 300, 1.98f, 62, 55)
                    )
                )
            )
        ),
        LevelConfig(
            id = 20,
            chapterId = 5,
            indexInChapter = 4,
            startGold = 283,
            carrotHp = 7,
            path = listOf(
                0 to 6, 1 to 6, 2 to 6, 2 to 5, 2 to 4, 2 to 3, 2 to 2, 2 to 1,
                2 to 0, 3 to 0, 4 to 0, 4 to 1, 4 to 2, 4 to 3, 4 to 4, 4 to 5,
                4 to 6, 4 to 7, 5 to 7, 6 to 7, 6 to 6, 6 to 5, 6 to 4, 6 to 3,
                6 to 2, 6 to 1, 7 to 1, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5,
                8 to 6, 9 to 6, 10 to 6, 11 to 6, 11 to 5, 11 to 4, 11 to 3, 11 to 2,
                11 to 1, 11 to 0, 12 to 0, 13 to 0, 13 to 1, 13 to 2, 13 to 3, 13 to 4,
                13 to 5, 14 to 5, 14 to 4, 14 to 3, 14 to 2, 14 to 1, 15 to 1
            ),
            permanent = listOf(15 to 0, 15 to 2, 0 to 5, 0 to 7),
            obstacles = listOf(
                ObstacleSpec(9, 1, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(3, 2, ObstacleKind.ROCK),
                ObstacleSpec(7, 2, ObstacleKind.TOXIC),
                ObstacleSpec(1, 3, ObstacleKind.ICE_BLOCK),
                ObstacleSpec(5, 3, ObstacleKind.CRATE),
                ObstacleSpec(12, 6, ObstacleKind.TOXIC),
                ObstacleSpec(10, 7, ObstacleKind.ICE_BLOCK)
            ),
            allowedTowers = listOf(TowerType.ARROW, TowerType.POISON, TowerType.ROCKET, TowerType.LIGHT, TowerType.SUN),
            waves = listOf(
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 15, 160, 2.00f, 35, 38)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 36, 177, 2.00f, 36, 12),
                        WaveGroup(EnemyKind.GRUNT, 9, 204, 2.00f, 36, 44)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 20, 195, 2.00f, 37, 22),
                        WaveGroup(EnemyKind.GRUNT, 10, 234, 2.00f, 37, 42)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 11, 212, 2.00f, 38, 38),
                        WaveGroup(EnemyKind.TANK, 7, 265, 2.00f, 57, 56)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 19, 230, 2.00f, 38, 38)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.SWARM, 38, 248, 2.00f, 39, 12),
                        WaveGroup(EnemyKind.GRUNT, 9, 285, 2.00f, 39, 44)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.RUNNER, 24, 265, 2.00f, 40, 22),
                        WaveGroup(EnemyKind.GRUNT, 10, 318, 2.00f, 40, 42)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 11, 283, 2.00f, 41, 38),
                        WaveGroup(EnemyKind.TANK, 7, 354, 2.00f, 62, 56)
                    )
                ),
                WaveConfig(
                    listOf(
                        WaveGroup(EnemyKind.GRUNT, 23, 300, 2.00f, 42, 38),
                        WaveGroup(EnemyKind.BOSS, 2, 540, 2.00f, 126, 150)
                    ), restFrames = 150
                )
            )
        )
    )

    val default: LevelConfig = all.first()

    fun byId(id: Int): LevelConfig = all.firstOrNull { it.id == id } ?: default

    /** 第一關永遠開放，之後每一關需要通過前一關。 */
    fun isUnlocked(levelId: Int, completed: Set<Int>): Boolean =
        levelId <= 1 || (levelId - 1) in completed

    fun nextLevel(levelId: Int): LevelConfig? = all.firstOrNull { it.id == levelId + 1 }
}
