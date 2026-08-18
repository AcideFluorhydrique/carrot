package io.github.acidefluorhydrique.carrot

enum class GameStatus { PLAYING, PAUSED, VICTORY, DEFEAT }

object GameState {

    const val MAX_SPEED_STEP = 3

    var level: LevelConfig = GameLevels.default
    var carrotHp: Int = 10
    var maxCarrotHp: Int = 10
    var gold: Int = 100
    var wave: Int = 0
    var status: GameStatus = GameStatus.PLAYING

    /** 遊戲速度倍率：1x / 2x / 3x。 */
    var speed: Int = 1

    var kills: Int = 0
    var goldEarned: Int = 0
    var leaks: Int = 0

    /** 蘿蔔剛被咬到時的閃爍計時，用於視覺回饋。 */
    var carrotHurtTimer: Int = 0

    val score: Int
        get() = kills * 12 + goldEarned + carrotHp * 120 + wave * 60

    /** 依剩餘蘿蔔血量給星：滿血 3 星、過半 2 星、有剩 1 星。 */
    val stars: Int
        get() = when {
            carrotHp <= 0 -> 0
            carrotHp >= maxCarrotHp -> 3
            carrotHp * 2 >= maxCarrotHp -> 2
            else -> 1
        }

    fun reset(selectedLevel: LevelConfig = level) {
        level = selectedLevel
        maxCarrotHp = selectedLevel.carrotHp
        carrotHp = selectedLevel.carrotHp
        gold = selectedLevel.startGold
        wave = 0
        status = GameStatus.PLAYING
        speed = 1
        kills = 0
        goldEarned = 0
        leaks = 0
        carrotHurtTimer = 0
    }

    fun cycleSpeed() {
        speed = if (speed >= MAX_SPEED_STEP) 1 else speed + 1
    }

    fun addGold(amount: Int) {
        if (amount <= 0) return
        gold += amount
        goldEarned += amount
    }

    fun spendGold(amount: Int): Boolean {
        if (amount > gold) return false
        gold -= amount
        return true
    }

    /** 敵人抵達終點，扣蘿蔔血。 */
    fun onEnemyReached(kind: EnemyKind) {
        leaks++
        carrotHp -= kind.leakDamage
        carrotHurtTimer = 30
        Audio.play(Sfx.HURT)
        Fx.shake(Ui.dp(5f), 18)
        if (carrotHp <= 0) {
            carrotHp = 0
            status = GameStatus.DEFEAT
        }
    }

    fun tickTimers() {
        if (carrotHurtTimer > 0) carrotHurtTimer--
    }
}
