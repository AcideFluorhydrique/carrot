// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class EnemySnapshot(
    val kind: String,
    val pathIndex: Int,
    val distanceTravelled: Float,
    val x: Float,
    val y: Float,
    val hp: Int,
    val maxHp: Int,
    val baseSpeed: Float,
    val goldReward: Int,
    val slowFactor: Float,
    val slowTimer: Int,
    val poisonDamage: Int,
    val poisonTimer: Int
)

data class EnemyManagerSnapshot(
    val phase: Int,
    val waveIndex: Int,
    val groupIndex: Int,
    val spawnedInGroup: Int,
    val spawnTimer: Int,
    val restTimer: Int,
    val enemies: List<EnemySnapshot>
)

data class ObstacleSnapshot(
    val col: Int,
    val row: Int,
    val kind: String,
    val hp: Int
)

data class TowerSnapshot(
    val col: Int,
    val row: Int,
    val type: String,
    val level: Int,
    val cooldown: Int,
    val aimAngle: Float,
    val invested: Int,
    val targetMode: String
)

data class GameSave(
    val version: Int,
    val levelId: Int,
    val carrotHp: Int,
    val maxCarrotHp: Int,
    val gold: Int,
    val wave: Int,
    val kills: Int,
    val goldEarned: Int,
    val leaks: Int,
    val speed: Int,
    val enemyManager: EnemyManagerSnapshot,
    val towers: List<TowerSnapshot>,
    val obstacles: List<ObstacleSnapshot>,
    val savedAt: Long
)

class SaveRepository(context: Context) {

    private val prefs = context.getSharedPreferences("carrot_save", Context.MODE_PRIVATE)

    init {
        migrateLegacyStars()
    }

    /**
     * 把舊鍵下的星等搬到 [KEY_STARS]。
     *
     * 覆蓋安裝時 SharedPreferences 會整份保留，所以玩家的通關紀錄本來就不會掉 ——
     * 真正會弄丟它的是「換一個鍵名」這個動作。舊鍵留著不刪，萬一玩家裝回舊版還能用。
     */
    private fun migrateLegacyStars() {
        if (prefs.contains(KEY_STARS)) return
        for (key in LEGACY_STAR_KEYS) {
            val legacy = prefs.getStringSet(key, null) ?: continue
            // getStringSet 回傳的是內部共用實例，必須複製後再寫回
            prefs.edit().putStringSet(KEY_STARS, HashSet(legacy)).apply()
            return
        }
    }

    // ---- 進行中的存檔 ----

    /** 便宜的檢查：不解析 JSON，只看鍵與版本號。 */
    fun hasActiveSave(): Boolean =
        prefs.contains(KEY_ACTIVE_SAVE) && prefs.getInt(KEY_ACTIVE_VERSION, 0) == SAVE_VERSION

    fun saveGame(save: GameSave) {
        runCatching {
            prefs.edit()
                .putString(KEY_ACTIVE_SAVE, save.toJson().toString())
                .putInt(KEY_ACTIVE_VERSION, save.version)
                .apply()
        }
    }

    fun loadGame(): GameSave? {
        val raw = prefs.getString(KEY_ACTIVE_SAVE, null) ?: return null
        val save = runCatching { JSONObject(raw).toGameSave() }.getOrNull() ?: return null
        if (save.version != SAVE_VERSION) {
            clearActiveSave()
            return null
        }
        return save
    }

    fun clearActiveSave() {
        prefs.edit().remove(KEY_ACTIVE_SAVE).remove(KEY_ACTIVE_VERSION).apply()
    }

    // ---- 關卡進度（星等）----

    fun recordResult(levelId: Int, stars: Int) {
        if (stars <= 0) return
        val current = starsFor(levelId)
        if (stars <= current) return
        val map = starMap().toMutableMap()
        map[levelId] = stars
        prefs.edit()
            .putStringSet(KEY_STARS, map.map { "${it.key}:${it.value}" }.toSet())
            .apply()
    }

    fun starsFor(levelId: Int): Int = starMap()[levelId] ?: 0

    /** 一次取出全部星等，供畫面快取，避免每一影格重複解析。 */
    fun allStars(): Map<Int, Int> = starMap()

    fun completedLevels(): Set<Int> = starMap().filterValues { it > 0 }.keys

    fun totalStars(): Int = starMap().values.sum()

    fun resetProgress() {
        prefs.edit()
            .remove(KEY_STARS)
            .remove(KEY_ACTIVE_SAVE)
            .remove(KEY_ACTIVE_VERSION)
            .apply()
    }

    private fun starMap(): Map<Int, Int> {
        val raw = prefs.getStringSet(KEY_STARS, emptySet()).orEmpty()
        val result = HashMap<Int, Int>()
        for (entry in raw) {
            val parts = entry.split(":")
            if (parts.size != 2) continue
            val id = parts[0].toIntOrNull() ?: continue
            val stars = parts[1].toIntOrNull() ?: continue
            result[id] = stars
        }
        return result
    }

    // ---- 設定 ----

    fun soundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND, true)

    fun musicEnabled(): Boolean = prefs.getBoolean(KEY_MUSIC, true)

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setMusicEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MUSIC, enabled).apply()
    }

    fun lastLevelId(): Int = prefs.getInt(KEY_LAST_LEVEL, GameLevels.default.id)

    fun setLastLevelId(id: Int) {
        prefs.edit().putInt(KEY_LAST_LEVEL, id).apply()
    }

    // ---- JSON ----

    private fun GameSave.toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("levelId", levelId)
        .put("carrotHp", carrotHp)
        .put("maxCarrotHp", maxCarrotHp)
        .put("gold", gold)
        .put("wave", wave)
        .put("kills", kills)
        .put("goldEarned", goldEarned)
        .put("leaks", leaks)
        .put("speed", speed)
        .put("savedAt", savedAt)
        .put("enemyManager", enemyManager.toJson())
        .put("towers", JSONArray().also { array -> towers.forEach { array.put(it.toJson()) } })
        .put("obstacles", JSONArray().also { array -> obstacles.forEach { array.put(it.toJson()) } })

    private fun EnemyManagerSnapshot.toJson(): JSONObject = JSONObject()
        .put("phase", phase)
        .put("waveIndex", waveIndex)
        .put("groupIndex", groupIndex)
        .put("spawnedInGroup", spawnedInGroup)
        .put("spawnTimer", spawnTimer)
        .put("restTimer", restTimer)
        .put("enemies", JSONArray().also { array -> enemies.forEach { array.put(it.toJson()) } })

    private fun EnemySnapshot.toJson(): JSONObject = JSONObject()
        .put("kind", kind)
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
        .put("poisonDamage", poisonDamage)
        .put("poisonTimer", poisonTimer)

    private fun ObstacleSnapshot.toJson(): JSONObject = JSONObject()
        .put("col", col)
        .put("row", row)
        .put("kind", kind)
        .put("hp", hp)

    private fun TowerSnapshot.toJson(): JSONObject = JSONObject()
        .put("col", col)
        .put("row", row)
        .put("type", type)
        .put("level", level)
        .put("cooldown", cooldown)
        .put("aimAngle", aimAngle.toDouble())
        .put("invested", invested)
        .put("targetMode", targetMode)

    private fun JSONObject.toGameSave(): GameSave = GameSave(
        version = optInt("version", 0),
        levelId = getInt("levelId"),
        carrotHp = getInt("carrotHp"),
        maxCarrotHp = optInt("maxCarrotHp", getInt("carrotHp")),
        gold = getInt("gold"),
        wave = getInt("wave"),
        kills = optInt("kills", 0),
        goldEarned = optInt("goldEarned", 0),
        leaks = optInt("leaks", 0),
        speed = optInt("speed", 1),
        enemyManager = getJSONObject("enemyManager").toEnemyManagerSnapshot(),
        towers = getJSONArray("towers").mapObjects { it.toTowerSnapshot() },
        obstacles = optJSONArray("obstacles")?.mapObjects { it.toObstacleSnapshot() } ?: emptyList(),
        savedAt = optLong("savedAt", 0L)
    )

    private fun JSONObject.toEnemyManagerSnapshot(): EnemyManagerSnapshot = EnemyManagerSnapshot(
        phase = optInt("phase", EnemyManager.PHASE_PREPARING),
        waveIndex = optInt("waveIndex", 0),
        groupIndex = optInt("groupIndex", 0),
        spawnedInGroup = optInt("spawnedInGroup", 0),
        spawnTimer = optInt("spawnTimer", 0),
        restTimer = optInt("restTimer", 0),
        enemies = getJSONArray("enemies").mapObjects { it.toEnemySnapshot() }
    )

    private fun JSONObject.toEnemySnapshot(): EnemySnapshot = EnemySnapshot(
        kind = optString("kind", EnemyKind.GRUNT.name),
        pathIndex = getInt("pathIndex"),
        distanceTravelled = getDouble("distanceTravelled").toFloat(),
        x = getDouble("x").toFloat(),
        y = getDouble("y").toFloat(),
        hp = getInt("hp"),
        maxHp = getInt("maxHp"),
        baseSpeed = getDouble("baseSpeed").toFloat(),
        goldReward = getInt("goldReward"),
        slowFactor = getDouble("slowFactor").toFloat(),
        slowTimer = getInt("slowTimer"),
        poisonDamage = optInt("poisonDamage", 0),
        poisonTimer = optInt("poisonTimer", 0)
    )

    private fun JSONObject.toObstacleSnapshot(): ObstacleSnapshot = ObstacleSnapshot(
        col = getInt("col"),
        row = getInt("row"),
        kind = optString("kind", ObstacleKind.ROCK.name),
        hp = getInt("hp")
    )

    private fun JSONObject.toTowerSnapshot(): TowerSnapshot = TowerSnapshot(
        col = getInt("col"),
        row = getInt("row"),
        type = optString("type", TowerType.ARROW.name),
        level = getInt("level"),
        cooldown = getInt("cooldown"),
        aimAngle = getDouble("aimAngle").toFloat(),
        invested = optInt("invested", 50),
        targetMode = optString("targetMode", TargetMode.FIRST.name)
    )

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        val result = ArrayList<T>()
        for (i in 0 until length()) {
            result.add(transform(getJSONObject(i)))
        }
        return result
    }

    companion object {
        const val SAVE_VERSION = 4
        private const val KEY_ACTIVE_SAVE = "active_save"
        private const val KEY_ACTIVE_VERSION = "active_save_version"
        /**
         * 通關星等。這個字串永遠不要再改 —— 它是玩家唯一不該因為版本更新而消失的資料，
         * 換掉它等於把所有人的進度歸零。要調整格式就寫遷移（見 migrateLegacyStars）。
         */
        private const val KEY_STARS = "level_stars"

        /** 歷史上用過的鍵，只在遷移時讀。新的鍵不要加版本後綴。 */
        private val LEGACY_STAR_KEYS = listOf("level_stars_v3")
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_MUSIC = "music_enabled"
        private const val KEY_LAST_LEVEL = "last_level"
    }
}
