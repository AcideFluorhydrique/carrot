package io.github.acidefluorhydrique.carrot

data class WaveConfig(
    val count: Int,
    val hp: Int,
    val speed: Float,
    val reward: Int,
    val spawnInterval: Int
)

data class LevelConfig(
    val id: Int,
    val name: String,
    val subtitle: String,
    val startGold: Int,
    val carrotHp: Int,
    val path: List<Pair<Int, Int>>,
    val waves: List<WaveConfig>
)

object GameLevels {
    val all = listOf(
        LevelConfig(
            id = 1,
            name = "菜園小徑",
            subtitle = "適合練習放塔與升級",
            startGold = 130,
            carrotHp = 12,
            path = listOf(
                0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0, 6 to 0,
                6 to 1,
                6 to 2, 7 to 2, 8 to 2, 9 to 2, 10 to 2, 11 to 2, 12 to 2, 13 to 2,
                13 to 3,
                13 to 4, 14 to 4, 15 to 4
            ),
            waves = listOf(
                WaveConfig(count = 8, hp = 3, speed = 1.7f, reward = 10, spawnInterval = 55),
                WaveConfig(count = 10, hp = 5, speed = 1.9f, reward = 12, spawnInterval = 48),
                WaveConfig(count = 8, hp = 8, speed = 2.1f, reward = 16, spawnInterval = 45)
            )
        ),
        LevelConfig(
            id = 2,
            name = "彎彎田埂",
            subtitle = "路線更長，炸彈塔開始好用",
            startGold = 150,
            carrotHp = 10,
            path = listOf(
                0 to 5, 1 to 5, 2 to 5, 3 to 5,
                3 to 4, 3 to 3, 3 to 2,
                4 to 2, 5 to 2, 6 to 2, 7 to 2,
                7 to 3, 7 to 4, 7 to 5, 7 to 6,
                8 to 6, 9 to 6, 10 to 6, 11 to 6, 12 to 6,
                12 to 5, 12 to 4, 12 to 3,
                13 to 3, 14 to 3, 15 to 3
            ),
            waves = listOf(
                WaveConfig(count = 10, hp = 5, speed = 1.8f, reward = 12, spawnInterval = 46),
                WaveConfig(count = 12, hp = 8, speed = 2.0f, reward = 15, spawnInterval = 40),
                WaveConfig(count = 10, hp = 13, speed = 2.2f, reward = 20, spawnInterval = 38),
                WaveConfig(count = 6, hp = 22, speed = 1.7f, reward = 30, spawnInterval = 58)
            )
        ),
        LevelConfig(
            id = 3,
            name = "夜色農場",
            subtitle = "敵人更快，需要冰塔控場",
            startGold = 180,
            carrotHp = 9,
            path = listOf(
                0 to 7, 1 to 7, 2 to 7, 3 to 7, 4 to 7,
                4 to 6, 4 to 5, 4 to 4,
                5 to 4, 6 to 4, 7 to 4, 8 to 4,
                8 to 3, 8 to 2, 8 to 1,
                9 to 1, 10 to 1, 11 to 1, 12 to 1,
                12 to 2, 12 to 3, 12 to 4, 12 to 5,
                13 to 5, 14 to 5, 15 to 5
            ),
            waves = listOf(
                WaveConfig(count = 12, hp = 6, speed = 2.1f, reward = 13, spawnInterval = 38),
                WaveConfig(count = 14, hp = 10, speed = 2.35f, reward = 16, spawnInterval = 34),
                WaveConfig(count = 12, hp = 16, speed = 2.45f, reward = 22, spawnInterval = 32),
                WaveConfig(count = 8, hp = 30, speed = 1.85f, reward = 36, spawnInterval = 50),
                WaveConfig(count = 1, hp = 120, speed = 1.35f, reward = 120, spawnInterval = 60)
            )
        )
    )

    val default: LevelConfig = all.first()
}
