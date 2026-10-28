// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot.bot

import io.github.acidefluorhydrique.carrot.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模擬器本身的健全性：跑得完、結果可重現。搜尋全靠這兩點。 */
class SimTest {

    @Test
    fun everyLevelPlaysToTheEnd() {
        for (difficulty in listOf("normal", "hard")) {
            for (id in 1..20) {
                val target = Target.parse("$difficulty-$id")
                val plan = Seeder.seeds(Geometry(target.level), endless = false).first()
                val outcome = Bot(plan).play(target)
                assertTrue("${target.name} did not finish", outcome.won || outcome.carrotHp == 0)
                assertTrue("${target.name} built nothing", outcome.invested > 0)
            }
        }
    }

    @Test
    fun sameplanSameResult() {
        val target = Target.parse("normal-12")
        val plan = Seeder.seeds(Geometry(target.level), endless = false).last()
        val first = Bot(plan).play(target, record = true)
        val second = Bot(plan).play(target, record = true)
        assertEquals(first, second)
    }

    @Test
    fun endlessRunsWithThePlayersLayout() {
        val target = Target.parse("endless")
        val geo = Geometry(target.level)
        val plan = Seeder.fromLayout(geo, Seeder.PLAYER_ENDLESS_LAYOUT, nukeAt = 0.85f)
        assertTrue(plan.builds.size > 60)
        val outcome = Bot(plan).play(target, maxWave = 12)
        assertTrue("died at wave ${outcome.wave}", outcome.carrotHp > 0)
        assertEquals(13, outcome.wave)
        assertTrue(GameState.isEndless)
    }

    @Test
    fun planTextRoundTrips() {
        val target = Target.parse("endless")
        for (plan in Seeder.seeds(Geometry(target.level), endless = true)) {
            assertEquals(plan, Plan.parse(plan.text()))
        }
    }
}
