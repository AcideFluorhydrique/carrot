// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot.bot

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * 機器人玩家的入口，由 .github/workflows/bot.yml 呼叫：
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*.BotRun' -Pbot.target=hard-12 -Pbot.minutes=15
 *
 * 沒給 bot.target 時跳過，所以一般的 `testDebugUnitTest` 不會跑它。
 *
 * 輸出到 bot.out（預設 app/build/bot）：
 * - `<target>.md`：結果、最終佈局、手速測試、逐步操作紀錄。
 * - `<target>.plan.txt`：計畫本身，放進 tools/bot/plans/ 就會成為下一次搜尋的起點之一。
 * - `<target>.row.md`：給總表用的一列。
 */
class BotRun {

    @Test
    fun run() {
        val name = System.getProperty("bot.target").orEmpty()
        assumeTrue("pass -Pbot.target=normal-N, hard-N or endless", name.isNotBlank())
        val minutes = System.getProperty("bot.minutes")?.toDoubleOrNull() ?: 10.0
        val out = File(System.getProperty("bot.out") ?: "build/bot").apply { mkdirs() }

        val target = Target.parse(name)
        val geo = Geometry(target.level)
        val started = System.currentTimeMillis()
        val deadline = started + (minutes * 60_000).toLong()

        val seeds = ArrayList<Plan>()
        // bot.yml 的 from_run 會把上一輪的計畫放成 <target>.plan.txt，原本提交的那份改名成 .committed，兩份都當起點
        for (suffix in listOf("plan.txt", "committed.plan.txt")) {
            val saved = File("../tools/bot/plans/${target.name}.$suffix")
            if (saved.isFile) seeds += Plan.parse(saved.readText())
        }
        val layout = target.layout
        if (layout == null) {
            seeds += Seeder.seeds(geo, target.endless)
            // 賣塔只用在一般關卡的最後一波；每個起點都給一份開著的，讓搜尋兩邊都看得到
            if (!target.endless) seeds += seeds.toList().map { it.copy(salvage = !it.salvage) }
        } else {
            val base = Seeder.fromLayout(geo, layout, nukeAt = 0.85f)
            seeds += base
            seeds += base.copy(call = CallPolicy.WHEN_CLEAR)
        }

        // 每次執行換一組亂數，重跑同一關才會走不同的路，而不是把上次走過的路再走一遍
        val seed = System.getProperty("bot.seed")?.toLongOrNull() ?: 0L
        val search = Search(target, geo, Random(target.name.hashCode().toLong() xor seed)) { println("[${target.name}] $it") }
        val (plan, outcome) = search.run(seeds, deadline)
        val seconds = (System.currentTimeMillis() - started) / 1000

        // 同一份計畫換成較慢的反應重跑：60 影格是 1x 速度下的一秒
        val reactions = listOf(1, 15, 30, 60, 120)
        val robustness = reactions.map { it to Bot(plan, it).play(target) }
        val recorded = Bot(plan).play(target, record = true)

        File(out, "${target.name}.plan.txt").writeText(
            "# ${target.name}: ${search.describe(outcome)}\n" + plan.text()
        )
        File(out, "${target.name}.md").writeText(report(target, plan, outcome, recorded, robustness, search.evaluations, seconds))
        File(out, "${target.name}.row.md").writeText(
            "| ${target.name} | ${result(outcome, target)} | ${robustness.joinToString(" / ") { short(it.second, target) }} " +
                "| ${recorded.layout.sumOf { row -> row.count { it.isDigit() } }} | ${search.evaluations} |\n"
        )
        println("[${target.name}] done: ${search.describe(outcome)} after ${search.evaluations} evaluations in ${seconds}s")
    }

    private fun result(o: Outcome, target: Target): String = when {
        o.won -> "✅ win, carrot ${o.carrotHp}/${o.maxCarrotHp} " + "★".repeat(o.stars)
        target.endless -> "died in wave ${o.wave}" + if (o.nukes > 0) " (${o.nukes} nukes)" else ""
        o.bossDamage > 0f -> "❌ lost in wave ${o.wave}/${target.level.waves.size}, leaked ${o.leaked}/${o.maxCarrotHp}, boss took ${"%.0f".format(o.bossDamage * 100)}%"
        else -> "❌ lost in wave ${o.wave}/${target.level.waves.size}, leaked ${o.leaked}/${o.maxCarrotHp}"
    }

    private fun short(o: Outcome, target: Target): String = when {
        o.won -> "${o.carrotHp}/${o.maxCarrotHp}"
        target.endless -> "w${o.wave}"
        else -> "✗w${o.wave} -${o.leaked}"
    }

    private fun report(
        target: Target,
        plan: Plan,
        best: Outcome,
        recorded: Outcome,
        robustness: List<Pair<Int, Outcome>>,
        evaluations: Int,
        seconds: Long
    ): String = buildString {
        appendLine("## ${target.name}")
        appendLine()
        appendLine("**${result(best, target)}** · ${evaluations} games simulated in ${seconds / 60} min")
        appendLine()
        appendLine("Final layout (`--` path, `##` permanent, `()` obstacle left, letter + level = tower; ${Codes.legend}):")
        appendLine()
        appendLine("```")
        recorded.layout.forEach { appendLine(it) }
        appendLine("```")
        appendLine()
        appendLine("Early wave calls: ${plan.call}" + if (target.endless) ", nuke when an enemy has walked ${"%.0f".format(plan.nukeAt * 100)}% of the path" else ", sell passed towers in the last wave: ${plan.salvage}")
        appendLine()
        val leaks = recorded.log.filter { "LEAK " in it }
        if (leaks.isNotEmpty()) {
            appendLine("Leaks (${recorded.leaked} carrot HP in all):")
            appendLine()
            appendLine("```")
            leaks.forEach { appendLine(it) }
            appendLine("```")
            appendLine()
        }
        appendLine("Same plan with slower reactions (1 s = 60 frames at 1x):")
        appendLine()
        appendLine("| decides every | result | carrot | kills | gold left |")
        appendLine("|---|---|---|---|---|")
        for ((reaction, o) in robustness) {
            appendLine("| $reaction frames | ${result(o, target)} | ${o.carrotHp}/${o.maxCarrotHp} | ${o.kills} | ${o.gold} |")
        }
        appendLine()
        appendLine("<details><summary>Every action, in order</summary>")
        appendLine()
        appendLine("```")
        recorded.log.forEach { appendLine(it) }
        appendLine("```")
        appendLine("</details>")
        appendLine()
    }
}
