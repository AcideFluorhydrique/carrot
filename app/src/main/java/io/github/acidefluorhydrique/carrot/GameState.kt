package io.github.acidefluorhydrique.carrot

enum class GameStatus { PLAYING, VICTORY, DEFEAT }

object GameState {
    var carrotHp: Int = 10       // 蘿蔔血量
    var gold: Int = 100          // 金幣（後面放塔用）
    var wave: Int = 0            // 當前波次
    var status: GameStatus = GameStatus.PLAYING

    fun reset() {
        carrotHp = 10
        gold = 100
        wave = 0
        status = GameStatus.PLAYING
    }

    // 敵人抵達終點，扣蘿蔔血
    fun onEnemyReached() {
        carrotHp--
        if (carrotHp <= 0) {
            carrotHp = 0
            status = GameStatus.DEFEAT
        }
    }
}