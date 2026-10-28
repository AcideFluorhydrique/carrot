// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * tools/balance.py 用自己的一份公式模擬關卡與無盡模式。Kotlin 改了數值卻忘了改腳本，
 * 腳本就會繼續算出好看但錯誤的結論。這裡直接執行腳本，逐波比對它產生的波次和遊戲實際的波次。
 *
 * 浮點：遊戲用 Float，腳本用 double，四捨五入的邊界上可能差一，所以整數欄位容許差一。
 * 常數漏改造成的差距遠大於此。
 */
class BalanceScriptTest {

    companion object {
        private const val ENDLESS_WAVES = 120
        private val script = File("../tools/balance.py")

        /** 每一行：區塊 關卡 波 組 種類 數量 基礎血量 基礎獎勵 間隔 實際速度 */
        private var rows: List<List<String>> = emptyList()

        @BeforeClass
        @JvmStatic
        fun runScript() {
            if (!script.isFile) return
            val code = """
                import sys
                sys.path.insert(0, sys.argv[1])
                import balance as b
                def emit(block, lid, waves):
                    for w, groups in enumerate(waves):
                        for g, x in enumerate(groups):
                            print(block, lid, w, g, x.kind, x.count, x.raw_hp, x.raw_reward, x.interval, repr(x.speed))
                emit("endless", 0, [b.endless_wave(i) for i in range($ENDLESS_WAVES)])
                for d in ("normal", "hard"):
                    for lv in b.levels_for(d):
                        emit(d, lv.id, lv.waves)
            """.trimIndent()
            val process = runCatching {
                ProcessBuilder("python3", "-c", code, script.absoluteFile.parent)
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull() ?: return
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(60, TimeUnit.SECONDS)
            check(process.exitValue() == 0) { "balance.py failed:\n$output" }
            rows = output.lines().filter { it.isNotBlank() }.map { it.trim().split(" ") }
        }
    }

    @Test
    fun endlessWavesMatch() {
        val script = block("endless")
        assertEquals(ENDLESS_WAVES, script.map { it[2] }.distinct().size)
        for (row in script) {
            val w = row[2].toInt()
            val g = row[3].toInt()
            compare("endless wave $w group $g", Endless.wave(w).groups[g], row)
        }
    }

    @Test
    fun normalLevelsMatch() = compareLevels("normal", Difficulty.NORMAL)

    @Test
    fun hardLevelsMatch() = compareLevels("hard", Difficulty.HARD)

    private fun compareLevels(name: String, difficulty: Difficulty) {
        val script = block(name)
        val groups = GameLevels.all.sumOf { level -> level.waves.sumOf { it.groups.size } }
        assertEquals("$name: group count", groups, script.size)
        for (row in script) {
            val level = difficulty.apply(GameLevels.byId(row[1].toInt()))
            val w = row[2].toInt()
            val g = row[3].toInt()
            compare("$name level ${level.id} wave $w group $g", level.waves[w].groups[g], row)
        }
    }

    private fun block(name: String): List<List<String>> {
        assumeTrue("python3 and tools/balance.py are needed", rows.isNotEmpty())
        return rows.filter { it[0] == name }
    }

    private fun compare(where: String, group: WaveGroup, row: List<String>) {
        assertEquals("$where kind", group.kind.name, row[4])
        near("$where count", group.count, row[5].toInt())
        near("$where hp", group.hp, row[6].toInt())
        near("$where reward", group.reward, row[7].toInt())
        near("$where interval", group.spawnInterval, row[8].toInt())
        assertEquals("$where speed", group.actualSpeed.toDouble(), row[9].toDouble(), 1e-4)
    }

    private fun near(where: String, kotlin: Int, python: Int) {
        val tolerance = maxOf(1L, abs(kotlin.toLong()) / 100_000)
        assertTrue("$where: game $kotlin, balance.py $python", abs(kotlin.toLong() - python) <= tolerance)
    }
}
