// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 規則本身的不變量：關卡資料、無盡波次、難度、核彈。 */
class RulesTest {

    // ---- 關卡資料 ----

    @Test
    fun levelIdsAreOneToTwenty() {
        assertEquals((1..20).toList(), GameLevels.all.map { it.id }.sorted())
    }

    @Test
    fun pathsAreContiguousAndInsideTheMap() {
        for (level in allLevels()) {
            val path = level.path
            assertTrue("${name(level)}: path too short", path.size >= 2)
            for ((col, row) in path) {
                assertTrue("${name(level)}: ($col,$row) off map", col in 0 until GameMap.COLS && row in 0 until GameMap.ROWS)
            }
            for (i in 1 until path.size) {
                val (c0, r0) = path[i - 1]
                val (c1, r1) = path[i]
                assertEquals("${name(level)}: gap between ${path[i - 1]} and ${path[i]}", 1, abs(c1 - c0) + abs(r1 - r0))
            }
            assertEquals("${name(level)}: path visits a cell twice", path.size, path.toSet().size)
        }
    }

    @Test
    fun terrainNeverSitsOnThePath() {
        for (level in allLevels()) {
            val path = level.path.toSet()
            val obstacles = level.obstacles.map { it.col to it.row }
            for (cell in level.permanent + obstacles) {
                assertTrue("${name(level)}: $cell is on the path", cell !in path)
                assertTrue("${name(level)}: $cell off map", cell.first in 0 until GameMap.COLS && cell.second in 0 until GameMap.ROWS)
            }
            val all = level.permanent + obstacles
            assertEquals("${name(level)}: terrain overlaps", all.size, all.toSet().size)
        }
    }

    @Test
    fun everyLevelHasAPlayableTowerAndWaves() {
        for (level in GameLevels.all) {
            val usable = level.allowedTowers.filter { level.id >= it.unlockAtLevel }
            assertTrue("${name(level)}: no unlocked tower", usable.isNotEmpty())
            assertTrue("${name(level)}: no waves", level.waves.isNotEmpty())
            for (wave in level.waves) {
                assertTrue("${name(level)}: empty wave", wave.groups.isNotEmpty())
                for (group in wave.groups) {
                    assertTrue("${name(level)}: bad group $group", group.count >= 1 && group.hp >= 1 && group.spawnInterval >= 1 && group.speed > 0f)
                }
            }
        }
    }

    // ---- 無盡模式 ----

    @Test
    fun endlessWavesGrowAndStayBounded() {
        var previousHp = 0
        for (index in 0 until 1000) {
            val wave = Endless.wave(index)
            val hp = wave.groups.first().hp
            assertTrue("wave $index hp $hp < $previousHp", hp >= previousHp)
            previousHp = hp
            assertEquals("wave $index boss", index % 10 == 9, wave.hasBoss())
            for (group in wave.groups) {
                assertTrue("wave $index group too big", group.count in 1..Endless.MAX_GROUP)
                assertTrue("wave $index hp overflow", group.actualHp >= 1)
                assertTrue("wave $index reward overflow", group.actualReward >= 1)
            }
        }
    }

    @Test
    fun endlessUsesFifthChapterMapWithEveryTower() {
        val level = Endless.level
        val map = GameLevels.byId(Endless.MAP_LEVEL_ID)
        assertEquals(map.path, level.path)
        assertEquals(map.obstacles, level.obstacles)
        assertEquals(TowerType.values().toList(), level.allowedTowers)
        assertEquals(Endless.START_GOLD, level.startGold)
        assertEquals(level, GameLevels.byId(Endless.LEVEL_ID))
    }

    // ---- 難度 ----

    @Test
    fun hardModeDoesNotStackAndKeepsTheFirstWave() {
        for (level in GameLevels.all) {
            val hard = Difficulty.HARD.apply(level)
            assertEquals(name(level), hard, Difficulty.HARD.apply(hard))
            assertEquals(name(level), level, Difficulty.NORMAL.apply(hard))
            assertEquals(name(level), level.waves.first(), hard.waves.first())
            assertEquals(name(level), level.waves.size, hard.waves.size)
            for ((w, wave) in hard.waves.withIndex()) {
                for ((g, group) in wave.groups.withIndex()) {
                    val base = level.waves[w].groups[g]
                    assertTrue("${name(level)} wave $w: hp shrank", group.hp >= base.hp)
                    assertTrue("${name(level)} wave $w: interval too tight", group.spawnInterval >= Difficulty.MIN_SPAWN_INTERVAL)
                    if (base.kind.isBoss) assertEquals("${name(level)} wave $w: boss count", base.count, group.count)
                    else assertTrue("${name(level)} wave $w: count shrank", group.count >= base.count)
                    assertEquals(base.reward, group.reward)
                }
            }
        }
    }

    // ---- 核彈 ----

    @Test
    fun nukePriceDoublesAndSaturates() {
        assertEquals(800, Nuke.price(0))
        assertEquals(1600, Nuke.price(1))
        assertEquals(6400, Nuke.price(3))
        assertEquals(Int.MAX_VALUE, Nuke.price(40))
        assertEquals(Int.MAX_VALUE, Nuke.price(1000))
    }

    @Test
    fun nukeBossFractionIsClamped() {
        assertEquals(1f, Nuke.bossFraction(800, 0), 0f)
        assertEquals(1f, Nuke.bossFraction(800, 100), 0f)
        assertEquals(0.5f, Nuke.bossFraction(800, Int.MAX_VALUE), 0f)
        assertEquals(0.75f, Nuke.bossFraction(1000, 4000), 1e-6f)
    }

    private fun allLevels(): List<LevelConfig> = GameLevels.all + Endless.level

    private fun name(level: LevelConfig): String =
        if (Endless.isEndless(level)) "endless" else "level ${level.id}"
}
