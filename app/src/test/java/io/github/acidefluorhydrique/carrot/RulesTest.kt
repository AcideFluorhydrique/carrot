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
            val wave = Endless.wave(index, Endless.FIXED_ORDER)
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
    fun seededEndlessWavesStayInShape() {
        for (seed in listOf(1L, -7L, 123456789L, Long.MIN_VALUE)) {
            var previousHp = 0
            for (index in 0 until 300) {
                val wave = Endless.wave(index, seed)
                val hp = wave.groups.first().hp
                assertTrue("seed $seed wave $index hp $hp < $previousHp", hp >= previousHp)
                previousHp = hp
                assertEquals("seed $seed wave $index boss", index % 10 == 9, wave.hasBoss())
            }
        }
    }

    /** 第一輪是教學，每一局都一樣；之後每一輪是同一批敵人組合換個順序。 */
    @Test
    fun aSeedOnlyReordersEachRound() {
        fun kinds(index: Int, seed: Long) = Endless.wave(index, seed).groups.map { it.kind }
        val seed = 987654321L
        for (index in 0 until 10) assertEquals("wave $index", Endless.wave(index, Endless.FIXED_ORDER), Endless.wave(index, seed))
        for (round in 1 until 30) {
            val fixed = (0 until 9).map { kinds(round * 10 + it, Endless.FIXED_ORDER).toString() }.sorted()
            val shuffled = (0 until 9).map { kinds(round * 10 + it, seed).toString() }.sorted()
            assertEquals("round $round", fixed, shuffled)
        }
    }

    @Test
    fun differentSeedsGiveDifferentRuns() {
        fun run(seed: Long) = (10 until 200).map { Endless.wave(it, seed).groups.map { g -> g.kind } }
        assertEquals(run(42L), run(42L))
        assertTrue(run(42L) != run(43L))
        assertTrue(run(42L) != run(Endless.FIXED_ORDER))
    }

    @Test
    fun aNewSeedIsNeverTheFixedOrder() {
        repeat(1000) { assertTrue(Endless.newSeed() != Endless.FIXED_ORDER) }
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
    fun freezePriceDoublesAndSaturates() {
        assertEquals(400, Freeze.price(0))
        assertEquals(800, Freeze.price(1))
        assertEquals(3200, Freeze.price(3))
        assertEquals(Int.MAX_VALUE, Freeze.price(40))
        assertEquals(Int.MAX_VALUE, Freeze.price(1000))
    }

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

    // ---- 漏怪 ----

    @Test
    fun aBossCostsLessTheMoreItIsHurt() {
        val boss = EnemyKind.BOSS
        assertEquals(5, boss.leakFor(1f))
        assertEquals(5, boss.leakFor(0.81f))
        assertEquals(4, boss.leakFor(0.8f))
        assertEquals(3, boss.leakFor(0.6f))
        assertEquals(3, boss.leakFor(0.5f))
        assertEquals(1, boss.leakFor(0.2f))
        assertEquals("a boss that reaches the carrot always costs something", 1, boss.leakFor(0.01f))
        for (step in 0..100) {
            val ratio = step / 100f
            assertTrue(boss.leakFor(ratio) <= boss.leakFor((step + 1) / 100f))
        }
    }

    @Test
    fun otherEnemiesCostTheSameWhateverTheirHp() {
        for (kind in EnemyKind.values().filter { !it.isBoss }) {
            assertEquals(kind.leakDamage, kind.leakFor(1f))
            assertEquals(kind.leakDamage, kind.leakFor(0.01f))
        }
    }

    private fun allLevels(): List<LevelConfig> = GameLevels.all + Endless.level

    private fun name(level: LevelConfig): String =
        if (Endless.isEndless(level)) "endless" else "level ${level.id}"
}
