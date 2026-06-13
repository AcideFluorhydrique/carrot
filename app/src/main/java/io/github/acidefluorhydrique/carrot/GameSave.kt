package io.github.acidefluorhydrique.carrot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class EnemySnapshot(
    val pathIndex: Int,
    val distanceTravelled: Float,
    val x: Float,
    val y: Float,
    val hp: Int,
    val maxHp: Int,
    val baseSpeed: Float,
    val goldReward: Int,
    val slowFactor: Float,
    val slowTimer: Int
)

data class EnemyManagerSnapshot(
    val spawnTimer: Int,
    val spawnedInWave: Int,
    val waveIndex: Int,
    val interWaveTimer: Int,
    val enemies: List<EnemySnapshot>
)

data class TowerSnapshot(
    val col: Int,
    val row: Int,
    val type: TowerType,
    val level: Int,
    val cooldown: Int,
    val aimAngle: Float
)

data class GameSave(
    val version: Int,
    val levelId: Int,
    val carrotHp: Int,
    val gold: Int,
    val wave: Int,
    val enemyManager: EnemyManagerSnapshot,
    val towers: List<TowerSnapshot>,
    val savedAt: Long
)

class SaveRepository(context: Context) {

    private val prefs = context.getSharedPreferences("carrot_save", Context.MODE_PRIVATE)

    fun hasActiveSave(): Boolean = prefs.contains(KEY_ACTIVE_SAVE)

    fun saveGame(save: GameSave) {
        prefs.edit()
            .putString(KEY_ACTIVE_SAVE, save.toJson().toString())
            .apply()
    }

    fun loadGame(): GameSave? {
        val raw = prefs.getString(KEY_ACTIVE_SAVE, null) ?: return null
        return runCatching { JSONObject(raw).toGameSave() }.getOrNull()
    }

    fun clearActiveSave() {
        prefs.edit().remove(KEY_ACTIVE_SAVE).apply()
    }

    fun markLevelCompleted(levelId: Int) {
        val completed = completedLevels().toMutableSet()
        completed.add(levelId)
        prefs.edit()
            .putStringSet(KEY_COMPLETED_LEVELS, completed.map { it.toString() }.toSet())
            .apply()
    }

    fun completedLevels(): Set<Int> {
        return prefs.getStringSet(KEY_COMPLETED_LEVELS, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()
    }

    private fun GameSave.toJson(): JSONObject {
        return JSONObject()
            .put("version", version)
            .put("levelId", levelId)
            .put("carrotHp", carrotHp)
            .put("gold", gold)
            .put("wave", wave)
            .put("savedAt", savedAt)
            .put("enemyManager", enemyManager.toJson())
            .put("towers", JSONArray().also { array ->
                towers.forEach { array.put(it.toJson()) }
            })
    }

    private fun EnemyManagerSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("spawnTimer", spawnTimer)
            .put("spawnedInWave", spawnedInWave)
            .put("waveIndex", waveIndex)
            .put("interWaveTimer", interWaveTimer)
            .put("enemies", JSONArray().also { array ->
                enemies.forEach { array.put(it.toJson()) }
            })
    }

    private fun EnemySnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("pathIndex", pathIndex)
            .put("distanceTravelled", distanceTravelled.toDouble())
            .put("x", x.toDouble())
            .put("y", y.toDouble())
            .put("hp", hp)
            .put("maxHp", maxHp)
            .put("baseSpeed", baseSpeed.toDouble())
            .put("goldReward", goldReward)
            .put("slowFactor", slowFactor.toDouble())
            .put("slowTimer", slowTimer)
    }

    private fun TowerSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("col", col)
            .put("row", row)
            .put("type", type.name)
            .put("level", level)
            .put("cooldown", cooldown)
            .put("aimAngle", aimAngle.toDouble())
    }

    private fun JSONObject.toGameSave(): GameSave {
        return GameSave(
            version = optInt("version", SAVE_VERSION),
            levelId = getInt("levelId"),
            carrotHp = getInt("carrotHp"),
            gold = getInt("gold"),
            wave = getInt("wave"),
            enemyManager = getJSONObject("enemyManager").toEnemyManagerSnapshot(),
            towers = getJSONArray("towers").mapObjects { it.toTowerSnapshot() },
            savedAt = optLong("savedAt", 0L)
        )
    }

    private fun JSONObject.toEnemyManagerSnapshot(): EnemyManagerSnapshot {
        return EnemyManagerSnapshot(
            spawnTimer = getInt("spawnTimer"),
            spawnedInWave = getInt("spawnedInWave"),
            waveIndex = getInt("waveIndex"),
            interWaveTimer = getInt("interWaveTimer"),
            enemies = getJSONArray("enemies").mapObjects { it.toEnemySnapshot() }
        )
    }

    private fun JSONObject.toEnemySnapshot(): EnemySnapshot {
        return EnemySnapshot(
            pathIndex = getInt("pathIndex"),
            distanceTravelled = getDouble("distanceTravelled").toFloat(),
            x = getDouble("x").toFloat(),
            y = getDouble("y").toFloat(),
            hp = getInt("hp"),
            maxHp = getInt("maxHp"),
            baseSpeed = getDouble("baseSpeed").toFloat(),
            goldReward = getInt("goldReward"),
            slowFactor = getDouble("slowFactor").toFloat(),
            slowTimer = getInt("slowTimer")
        )
    }

    private fun JSONObject.toTowerSnapshot(): TowerSnapshot {
        return TowerSnapshot(
            col = getInt("col"),
            row = getInt("row"),
            type = TowerType.valueOf(getString("type")),
            level = getInt("level"),
            cooldown = getInt("cooldown"),
            aimAngle = getDouble("aimAngle").toFloat()
        )
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        val result = mutableListOf<T>()
        for (i in 0 until length()) {
            result.add(transform(getJSONObject(i)))
        }
        return result
    }

    companion object {
        private const val SAVE_VERSION = 1
        private const val KEY_ACTIVE_SAVE = "active_save"
        private const val KEY_COMPLETED_LEVELS = "completed_levels"
    }
}
