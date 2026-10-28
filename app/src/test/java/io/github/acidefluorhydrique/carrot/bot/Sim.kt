// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot.bot

import io.github.acidefluorhydrique.carrot.Difficulty
import io.github.acidefluorhydrique.carrot.Endless
import io.github.acidefluorhydrique.carrot.EnemyManager
import io.github.acidefluorhydrique.carrot.Fx
import io.github.acidefluorhydrique.carrot.GameLevels
import io.github.acidefluorhydrique.carrot.GameMap
import io.github.acidefluorhydrique.carrot.GameState
import io.github.acidefluorhydrique.carrot.GameStatus
import io.github.acidefluorhydrique.carrot.LevelConfig
import io.github.acidefluorhydrique.carrot.Nuke
import io.github.acidefluorhydrique.carrot.ObstacleManager
import io.github.acidefluorhydrique.carrot.TargetMode
import io.github.acidefluorhydrique.carrot.Tower
import io.github.acidefluorhydrique.carrot.TowerManager
import io.github.acidefluorhydrique.carrot.TowerType

/**
 * 要打的關卡：`normal-5`、`hard-12` 或 `endless`。
 *
 * `endless@player`、`endless@bot` 固定一張陣型（[Seeder.LAYOUTS]），搜尋只調蓋塔與升級的先後、
 * 瞄準方式、核彈與叫波時機，不能換塔也不能搬塔，用來公平比較兩張陣型。
 */
data class Target(
    val name: String,
    val level: LevelConfig,
    val difficulty: Difficulty,
    val layout: List<String>? = null
) {

    val endless: Boolean get() = Endless.isEndless(level)

    companion object {
        fun parse(text: String): Target {
            val name = text.trim().lowercase()
            if (name == "endless") return Target(name, Endless.level, Difficulty.NORMAL)
            if (name.startsWith("endless@")) {
                val layout = Seeder.LAYOUTS[name.substringAfter('@')]
                    ?: throw IllegalArgumentException("unknown layout in $text, known: ${Seeder.LAYOUTS.keys}")
                return Target(name, Endless.level, Difficulty.NORMAL, layout)
            }
            val parts = name.split("-")
            require(parts.size == 2) { "target must be endless, normal-N or hard-N: $text" }
            val difficulty = when (parts[0]) {
                "normal" -> Difficulty.NORMAL
                "hard" -> Difficulty.HARD
                else -> throw IllegalArgumentException("unknown difficulty in $text")
            }
            val id = parts[1].toInt()
            require(GameLevels.all.any { it.id == id }) { "no level $id" }
            return Target(name, GameLevels.byId(id), difficulty)
        }
    }
}

/**
 * 不開畫面跑真正的遊戲邏輯。開局與每一步都照 GameView.startLevel / GameView.update 的順序
 * 呼叫同一批類別，蓋塔、升級、清障也走玩家點擊時的同一條路徑，規則改了這裡自動跟著變。
 *
 * Android 的繪圖類別在單元測試裡是回傳預設值的空殼，所以特效、音效、字串都是無害的空操作。
 * 遊戲邏輯沒有亂數（亂數只用在畫面上），同一份計畫每次跑出來的結果都一樣。
 *
 * [GameState] 是全域單例，同一時間只能有一個 Sim 在跑。
 */
class Sim(target: Target) {

    val level: LevelConfig = if (target.endless) Endless.level else target.difficulty.apply(target.level)
    val map = GameMap()
    val enemyManager = EnemyManager(map)
    val obstacleManager = ObstacleManager(map)
    val towerManager = TowerManager(map)

    var frames = 0
        private set

    init {
        GameState.difficulty = target.difficulty
        Fx.clear()
        Fx.showDamageNumbers = false
        GameState.reset(level)
        map.loadLevel(level)
        towerManager.reset()
        obstacleManager.reset(level)
        enemyManager.reset(level)
    }

    val isOver: Boolean get() = GameState.status != GameStatus.PLAYING

    /** 一個模擬步，等於 1x 速度下的一影格。 */
    fun step() {
        GameState.tickTimers()
        enemyManager.update()
        obstacleManager.update()
        towerManager.update(enemyManager.enemies, obstacleManager)
        // 特效只會越積越多，沒有人看就直接丟掉
        Fx.clear()
        frames++
    }

    fun towerAt(col: Int, row: Int): Tower? = towerManager.towers.firstOrNull { it.col == col && it.row == row }

    /** 和玩家一樣：選塔、按下、放開。成功時回傳新塔。 */
    fun build(col: Int, row: Int, type: TowerType, mode: TargetMode): Tower? {
        if (!type.isAvailable || !map.canPlaceTower(col, row) || GameState.gold < type.baseCost) return null
        towerManager.clearBuildType()
        towerManager.toggleBuildType(type)
        val (x, y) = map.centerOf(col, row)
        towerManager.onMapDown(x, y)
        towerManager.onMapUp()
        towerManager.clearBuildType()
        val tower = towerAt(col, row) ?: return null
        var guard = TargetMode.values().size
        while (tower.targetMode != mode && guard-- > 0) towerManager.cycleTargetModeOfSelected()
        towerManager.clearSelection()
        return tower
    }

    fun upgrade(tower: Tower): Boolean {
        if (tower.isMaxLevel || GameState.gold < tower.upgradeCost) return false
        val (x, y) = map.centerOf(tower.col, tower.row)
        towerManager.onMapDown(x, y)
        val done = towerManager.upgradeSelected()
        towerManager.clearSelection()
        return done
    }

    /** 標記障礙物集火。已經標記過的不再點，否則會變成取消標記。 */
    fun mark(col: Int, row: Int) {
        val obstacle = obstacleManager.aliveAt(col, row) ?: return
        if (!obstacle.isFocused) obstacleManager.onTap(col, row)
    }

    fun callNextWave(): Boolean {
        if (!enemyManager.canCallNextWave()) return false
        enemyManager.callNextWave()
        return true
    }

    fun nuke(): Boolean = level.id == Endless.LEVEL_ID && Nuke.tryFire(enemyManager.enemies)

    /** 地圖現況，每格兩個字：塔是種類代號加等級。 */
    fun layout(): List<String> = (0 until GameMap.ROWS).map { row ->
        buildString {
            for (col in 0 until GameMap.COLS) {
                val tower = towerAt(col, row)
                append(
                    when {
                        tower != null -> "${Codes.of(tower.type)}${tower.level}"
                        obstacleManager.aliveAt(col, row) != null -> "()"
                        map.grid[row][col] == GameMap.PATH -> "--"
                        map.grid[row][col] == GameMap.PERMANENT -> "##"
                        else -> " ."
                    }
                )
                if (col < GameMap.COLS - 1) append(' ')
            }
        }
    }
}

/** 報告與計畫檔裡的塔種代號。 */
object Codes {
    private val codes = mapOf(
        TowerType.ARROW to 'A', TowerType.ICE to 'I', TowerType.BOMB to 'B', TowerType.MOON to 'M',
        TowerType.POISON to 'P', TowerType.ROCKET to 'R', TowerType.LIGHT to 'L', TowerType.SUN to 'S'
    )

    fun of(type: TowerType): Char = codes.getValue(type)

    fun typeOf(code: Char): TowerType? = codes.entries.firstOrNull { it.value == code.uppercaseChar() }?.key

    val legend: String = codes.entries.joinToString("  ") { "${it.value}=${it.key.name}" }
}
