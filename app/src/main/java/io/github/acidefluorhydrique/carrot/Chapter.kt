// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Color

/**
 * 章節配色。每個章節有自己的天空、草地、路面與裝飾，
 * 讓五個章節看起來像五個不同的地方，而不是同一片綠地換波次。
 */
data class ChapterTheme(
    val skyTop: String,
    val skyMid: String,
    val skyBottom: String,
    /** 遠景色塊（山丘、霧氣）。 */
    val hill: String,
    val grassA: String,
    val grassB: String,
    val grassLine: String,
    val pathColor: String,
    val pathHighlight: String,
    val pathEdge: String,
    val frame: String,
    val accent: String,
    val decor: List<String>
) {
    // 每一格每一幀都會用到，字串解析只做一次
    val skyTopColor: Int by lazy { Color.parseColor(skyTop) }
    val skyMidColor: Int by lazy { Color.parseColor(skyMid) }
    val skyBottomColor: Int by lazy { Color.parseColor(skyBottom) }
    val hillColor: Int by lazy { Color.parseColor(hill) }
    val grassAColor: Int by lazy { Color.parseColor(grassA) }
    val grassBColor: Int by lazy { Color.parseColor(grassB) }
    val grassLineColor: Int by lazy { Color.parseColor(grassLine) }
    val pathColorInt: Int by lazy { Color.parseColor(pathColor) }
    val pathHighlightColor: Int by lazy { Color.parseColor(pathHighlight) }
    val pathEdgeColor: Int by lazy { Color.parseColor(pathEdge) }
    val frameColor: Int by lazy { Color.parseColor(frame) }
    val accentColor: Int by lazy { Color.parseColor(accent) }
}

data class Chapter(
    val id: Int,
    val nameRes: Int,
    val subtitleRes: Int,
    val emoji: String,
    val theme: ChapterTheme
) {
    val name: String get() = Strings.get(nameRes)
    val subtitle: String get() = Strings.get(subtitleRes)

    /** 章節內的關卡。用 lazy 是因為選關畫面每一幀都會問好幾次。 */
    val levels: List<LevelConfig> by lazy { GameLevels.all.filter { it.chapterId == id } }
}

object Chapters {

    val all: List<Chapter> = listOf(
        Chapter(
            id = 1,
            nameRes = R.string.chapter_1_name,
            subtitleRes = R.string.chapter_1_desc,
            emoji = "🌱",
            theme = ChapterTheme(
                skyTop = "#16302A", skyMid = "#1D4028", skyBottom = "#2A5A2C",
                hill = "#264F2C",
                grassA = "#3F793F", grassB = "#376F38", grassLine = "#331F361E",
                pathColor = "#A77935", pathHighlight = "#28FFF1C3", pathEdge = "#553E2813",
                frame = "#2A4423", accent = "#7BE88C",
                decor = listOf("🪨", "🌳")
            )
        ),
        Chapter(
            id = 2,
            nameRes = R.string.chapter_2_name,
            subtitleRes = R.string.chapter_2_desc,
            emoji = "🌾",
            theme = ChapterTheme(
                skyTop = "#3A2C13", skyMid = "#54401A", skyBottom = "#6B5220",
                hill = "#5E4A1E",
                grassA = "#8A7838", grassB = "#7E6D31", grassLine = "#33241C0E",
                pathColor = "#C4A063", pathHighlight = "#33FFF6DA", pathEdge = "#55574018",
                frame = "#4A3A18", accent = "#FFD75E",
                decor = listOf("🌾", "🪵")
            )
        ),
        Chapter(
            id = 3,
            nameRes = R.string.chapter_3_name,
            subtitleRes = R.string.chapter_3_desc,
            emoji = "🌙",
            theme = ChapterTheme(
                skyTop = "#0B1622", skyMid = "#132433", skyBottom = "#1B3540",
                hill = "#17303A",
                grassA = "#24433D", grassB = "#1E3B37", grassLine = "#330E1F1C",
                pathColor = "#6A5C48", pathHighlight = "#22CFE3FF", pathEdge = "#55241D12",
                frame = "#16302F", accent = "#8FD8FF",
                decor = listOf("🌲", "🍄")
            )
        ),
        Chapter(
            id = 4,
            nameRes = R.string.chapter_4_name,
            subtitleRes = R.string.chapter_4_desc,
            emoji = "❄️",
            theme = ChapterTheme(
                skyTop = "#13212C", skyMid = "#1D3646", skyBottom = "#2F5468",
                hill = "#28495C",
                grassA = "#4C6E7C", grassB = "#446471", grassLine = "#33172B33",
                pathColor = "#AFC9D8", pathHighlight = "#3AFFFFFF", pathEdge = "#55405C6B",
                frame = "#22414F", accent = "#BFEAFF",
                decor = listOf("❄️", "🪨")
            )
        ),
        Chapter(
            id = 5,
            nameRes = R.string.chapter_5_name,
            subtitleRes = R.string.chapter_5_desc,
            emoji = "🌋",
            theme = ChapterTheme(
                skyTop = "#180D0C", skyMid = "#2E1512", skyBottom = "#4A2019",
                hill = "#3E1C16",
                grassA = "#4A322F", grassB = "#432C2A", grassLine = "#33190D0C",
                pathColor = "#8A5236", pathHighlight = "#33FFB27A", pathEdge = "#55361A0F",
                frame = "#3A211A", accent = "#FF8A3D",
                decor = listOf("🌋", "🪨")
            )
        )
    )

    val default: Chapter = all.first()

    fun byId(id: Int): Chapter = all.firstOrNull { it.id == id } ?: default

    /** 第一章永遠開放；之後每章需要前一章至少通關一關以上的全部關卡。 */
    fun isUnlocked(chapterId: Int, completed: Set<Int>): Boolean {
        val index = all.indexOfFirst { it.id == chapterId }
        if (index <= 0) return true
        val previous = all[index - 1]
        return previous.levels.all { it.id in completed }
    }

    fun starsOf(chapterId: Int, stars: Map<Int, Int>): Int =
        byId(chapterId).levels.sumOf { stars[it.id] ?: 0 }

    fun maxStarsOf(chapterId: Int): Int = byId(chapterId).levels.size * 3
}
