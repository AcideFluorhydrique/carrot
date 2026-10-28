// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot.bot

import io.github.acidefluorhydrique.carrot.GameState
import io.github.acidefluorhydrique.carrot.TargetMode
import io.github.acidefluorhydrique.carrot.TowerType
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
        val outcome = Bot(plan).play(target, maxWave = 12, record = true)
        // 起點的蓋塔順序是猜的，撐多久交給搜尋；這裡只確認塔有在打，並把經過印出來
        println("endless with the player's layout: wave ${outcome.wave}, carrot ${outcome.carrotHp}, kills ${outcome.kills}")
        outcome.log.forEach(::println)
        outcome.layout.forEach(::println)
        assertTrue(GameState.isEndless)
        assertTrue("nothing was killed", outcome.kills > 0)
    }

    /** 1-1 只有箭塔，五座守住轉角一定過得了。過不了表示模擬器本身壞了，不是打法的問題。 */
    @Test
    fun handPlacedArrowsBeatTheFirstLevel() {
        val target = Target.parse("normal-1")
        val cells = listOf(6 to 3, 8 to 4, 6 to 1, 8 to 3, 6 to 4)
        val steps = ArrayList<Step>()
        cells.forEachIndexed { i, (col, row) -> steps += Build(i + 1, col, row, TowerType.ARROW, TargetMode.FIRST) }
        cells.indices.forEach { i -> steps += Upgrade(i + 1); steps += Upgrade(i + 1) }
        val outcome = Bot(Plan(steps)).play(target, record = true)
        outcome.log.forEach(::println)
        outcome.layout.forEach(::println)
        assertTrue("lost at wave ${outcome.wave}, kills ${outcome.kills}", outcome.won)
    }

    @Test
    fun planTextRoundTrips() {
        val target = Target.parse("endless")
        for (plan in Seeder.seeds(Geometry(target.level), endless = true)) {
            assertEquals(plan, Plan.parse(plan.text()))
        }
    }
}
