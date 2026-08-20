// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin

enum class MenuAction { NONE, CONTINUE, START, LEVELS, HELP, SETTINGS, BACK }

enum class SettingsAction { NONE, TOGGLE_SOUND, TOGGLE_MUSIC, CYCLE_LANGUAGE, RESET_PROGRESS, BACK }

class MenuRenderer {

    private val paint = Paint().apply { isAntiAlias = true }
    private var frame = 0

    fun tick() {
        frame++
    }

    // ---- 主選單 ----

    fun drawMain(
        canvas: Canvas,
        w: Int,
        h: Int,
        items: List<MenuAction>,
        currentLevel: LevelConfig,
        totalStars: Int,
        completedCount: Int
    ) {
        drawBackground(canvas, w, h)

        val leftX = w * 0.29f
        val titleWidth = w * 0.5f - Ui.dp(16f)
        Widgets.centeredFit(
            canvas, Strings.get(R.string.menu_title), leftX, h * 0.3f,
            Ui.dp(34f), titleWidth, bold = true, color = Colors.of("#FFF7D6")
        )
        Widgets.centeredFit(
            canvas, Strings.get(R.string.menu_tagline), leftX, h * 0.3f + Ui.dp(22f),
            Ui.dp(11.5f), titleWidth, color = Colors.of("#D3E4CC")
        )

        val bob = sin(frame * 0.05f) * Ui.dp(4f)
        paint.style = Paint.Style.FILL
        paint.textSize = Ui.dp(56f)
        val carrot = "🥕"
        canvas.drawText(carrot, leftX - paint.measureText(carrot) / 2f, h * 0.62f + bob, paint)

        Widgets.centeredFit(
            canvas,
            Strings.format(
                R.string.menu_progress,
                completedCount, GameLevels.all.size, totalStars, GameLevels.all.size * 3
            ),
            leftX, h * 0.76f, Ui.dp(11f), titleWidth, color = Colors.of("#C4D9C8")
        )
        Widgets.centeredFit(
            canvas, Strings.format(R.string.menu_current_level, currentLevel.fullName),
            leftX, h * 0.83f, Ui.dp(10.5f), titleWidth, color = Colors.of("#9FBDA6")
        )

        for (i in items.indices) {
            val rect = mainButtonRect(w, h, i, items.size)
            when (items[i]) {
                MenuAction.CONTINUE -> Widgets.button(canvas, rect, Strings.get(R.string.menu_continue), Widgets.AMBER_TOP, Widgets.AMBER_BOTTOM, textSize = Ui.dp(14f))
                MenuAction.START -> Widgets.button(canvas, rect, Strings.get(R.string.menu_start), Widgets.GREEN_TOP, Widgets.GREEN_BOTTOM, textSize = Ui.dp(14f))
                MenuAction.LEVELS -> Widgets.button(canvas, rect, Strings.get(R.string.menu_levels), Widgets.BLUE_TOP, Widgets.BLUE_BOTTOM, textSize = Ui.dp(14f))
                MenuAction.HELP -> Widgets.button(canvas, rect, Strings.get(R.string.menu_help), Widgets.PURPLE_TOP, Widgets.PURPLE_BOTTOM, textSize = Ui.dp(14f))
                MenuAction.SETTINGS -> Widgets.button(canvas, rect, Strings.get(R.string.menu_settings), Widgets.GRAY_TOP, Widgets.GRAY_BOTTOM, textSize = Ui.dp(14f))
                else -> Unit
            }
        }
    }

    fun mainTap(x: Float, y: Float, w: Int, h: Int, items: List<MenuAction>): MenuAction {
        for (i in items.indices) {
            if (mainButtonRect(w, h, i, items.size).contains(x, y)) return items[i]
        }
        return MenuAction.NONE
    }

    // ---- 章節選擇 ----

    fun drawChapters(canvas: Canvas, w: Int, h: Int, stars: Map<Int, Int>, completed: Set<Int>) {
        drawBackground(canvas, w, h)
        Widgets.centered(
            canvas, Strings.get(R.string.chapters_title), w / 2f, h * 0.13f,
            Ui.dp(24f), bold = true, color = Colors.of("#FFF7D6")
        )

        for (i in Chapters.all.indices) {
            val chapter = Chapters.all[i]
            val rect = chapterCardRect(w, h, i)
            val unlocked = Chapters.isUnlocked(chapter.id, completed)
            val earned = Chapters.starsOf(chapter.id, stars)
            val maximum = Chapters.maxStarsOf(chapter.id)
            val theme = chapter.theme

            Widgets.panel(
                canvas, rect,
                radius = Ui.dp(10f),
                topColor = if (unlocked) theme.skyMid else "#CC1B2320",
                bottomColor = if (unlocked) theme.skyTop else "#CC121815",
                borderColor = if (unlocked) theme.accent else "#33FFFFFF"
            )

            if (!unlocked) {
                Widgets.centered(canvas, "🔒", rect.centerX(), rect.centerY(), Ui.dp(24f), color = Color.WHITE)
                Widgets.centeredFit(
                    canvas, Strings.get(R.string.chapter_locked), rect.centerX(), rect.centerY() + Ui.dp(20f),
                    Ui.dp(9f), rect.width() - Ui.dp(10f), color = Colors.of("#A9B6AE")
                )
                continue
            }

            // 章節色帶
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = theme.accentColor
            paint.alpha = 60
            canvas.drawRoundRect(
                RectF(rect.left + Ui.dp(2f), rect.top + Ui.dp(2f), rect.right - Ui.dp(2f), rect.top + Ui.dp(30f)),
                Ui.dp(8f), Ui.dp(8f), paint
            )
            paint.alpha = 255

            Widgets.centered(canvas, chapter.emoji, rect.centerX(), rect.top + Ui.dp(24f), Ui.dp(19f))
            Widgets.centeredFit(
                canvas, Strings.format(R.string.chapter_number, chapter.id),
                rect.centerX(), rect.top + Ui.dp(45f), Ui.dp(9.5f), rect.width() - Ui.dp(10f),
                color = Colors.of("#B7C9BD")
            )
            Widgets.centeredFit(
                canvas, chapter.name, rect.centerX(), rect.top + Ui.dp(63f),
                Ui.dp(14f), rect.width() - Ui.dp(10f), bold = true, color = Colors.of("#FFFDF2")
            )
            drawWrapped(canvas, chapter.subtitle, rect, Ui.dp(9f), rect.top + Ui.dp(80f), Colors.of("#B9CCC0"))

            Widgets.centeredFit(
                canvas, Strings.format(R.string.chapter_stars, earned, maximum),
                rect.centerX(), rect.bottom - Ui.dp(10f), Ui.dp(10.5f), rect.width() - Ui.dp(10f),
                bold = true, color = Colors.of("#FFD75E")
            )
        }

        Widgets.button(canvas, backButtonRect(w, h), Strings.get(R.string.common_back), Widgets.GRAY_TOP, Widgets.GRAY_BOTTOM, textSize = Ui.dp(13f))
    }

    fun chapterTap(x: Float, y: Float, w: Int, h: Int, completed: Set<Int>): Chapter? {
        for (i in Chapters.all.indices) {
            if (!chapterCardRect(w, h, i).contains(x, y)) continue
            val chapter = Chapters.all[i]
            if (!Chapters.isUnlocked(chapter.id, completed)) {
                Audio.play(Sfx.DENY)
                return null
            }
            return chapter
        }
        return null
    }

    // ---- 章節內的關卡地圖：沿著一條彎曲小徑排列 ----

    fun drawLevelMap(
        canvas: Canvas,
        w: Int,
        h: Int,
        chapter: Chapter,
        stars: Map<Int, Int>,
        completed: Set<Int>
    ) {
        drawThemedBackground(canvas, w, h, chapter.theme)

        Widgets.centered(
            canvas, "${chapter.emoji}  ${chapter.name}", w / 2f, h * 0.13f,
            Ui.dp(22f), bold = true, color = Colors.of("#FFF7D6")
        )
        Widgets.centered(
            canvas, chapter.subtitle, w / 2f, h * 0.13f + Ui.dp(19f),
            Ui.dp(10.5f), color = Colors.of("#C8DACD")
        )

        val levels = chapter.levels
        if (levels.isEmpty()) return
        val count = levels.size

        drawTrack(canvas, w, h, levels, completed, chapter)

        for (i in levels.indices) {
            drawLevelNode(canvas, w, h, i, count, levels[i], stars, completed, chapter)
        }

        Widgets.button(canvas, backButtonRect(w, h), Strings.get(R.string.common_back), Widgets.GRAY_TOP, Widgets.GRAY_BOTTOM, textSize = Ui.dp(13f))
    }

    /** 節點之間的虛線小徑，走過的段落亮起來。 */
    private fun drawTrack(
        canvas: Canvas,
        w: Int,
        h: Int,
        levels: List<LevelConfig>,
        completed: Set<Int>,
        chapter: Chapter
    ) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        val dots = 9
        for (i in 0 until levels.size - 1) {
            val (x1, y1) = levelNodeCenter(w, h, i, levels.size)
            val (x2, y2) = levelNodeCenter(w, h, i + 1, levels.size)
            val cleared = levels[i].id in completed
            for (d in 1 until dots) {
                val t = d.toFloat() / dots
                paint.color = if (cleared) chapter.theme.accentColor else Colors.of("#66000000")
                paint.alpha = if (cleared) 210 else 90
                canvas.drawCircle(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, Ui.dp(2.6f), paint)
            }
        }
        paint.alpha = 255
    }

    private fun drawLevelNode(
        canvas: Canvas,
        w: Int,
        h: Int,
        index: Int,
        count: Int,
        level: LevelConfig,
        stars: Map<Int, Int>,
        completed: Set<Int>,
        chapter: Chapter
    ) {
        val (cx, cy) = levelNodeCenter(w, h, index, count)
        val r = levelNodeRadius()
        val unlocked = GameLevels.isUnlocked(level.id, completed)
        val cleared = level.id in completed
        val earned = stars[level.id] ?: 0
        val isNext = unlocked && !cleared

        paint.style = Paint.Style.FILL
        paint.shader = null

        // 下一個要打的關卡：脈動光圈
        if (isNext) {
            val pulse = 1f + 0.16f * sin(frame * 0.09f)
            paint.color = chapter.theme.accentColor
            paint.alpha = 70
            canvas.drawCircle(cx, cy, r * 1.42f * pulse, paint)
            paint.alpha = 255
        }

        paint.color = Colors.of("#77000000")
        canvas.drawCircle(cx, cy + Ui.dp(2.5f), r, paint)

        paint.color = when {
            !unlocked -> Colors.of("#3C4650")
            cleared -> Colors.of("#2F7E3C")
            else -> chapter.theme.accentColor
        }
        canvas.drawCircle(cx, cy, r, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = Ui.dp(2f)
        paint.color = when {
            !unlocked -> Colors.of("#55FFFFFF")
            isNext -> Colors.of("#FFFFFFFF")
            else -> Colors.of("#88FFFFFF")
        }
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.FILL

        if (!unlocked) {
            Widgets.centered(canvas, "🔒", cx, cy + Ui.dp(6f), Ui.dp(15f), color = Color.WHITE)
            return
        }

        Widgets.centered(
            canvas, level.indexInChapter.toString(), cx, cy + Ui.dp(7f),
            Ui.dp(19f), bold = true, color = Colors.of("#FFFDF2")
        )
        if (level.hasBoss()) {
            Widgets.centered(canvas, "👹", cx + r * 0.78f, cy - r * 0.5f, Ui.dp(13f), color = Color.WHITE)
        }
        Widgets.stars(canvas, cx, cy + r + Ui.dp(15f), earned, Ui.dp(11f))
    }

    fun levelMapTap(x: Float, y: Float, w: Int, h: Int, chapter: Chapter, completed: Set<Int>): LevelConfig? {
        val levels = chapter.levels
        val r = levelNodeRadius() * 1.35f
        for (i in levels.indices) {
            val (cx, cy) = levelNodeCenter(w, h, i, levels.size)
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > r * r) continue
            val level = levels[i]
            if (!GameLevels.isUnlocked(level.id, completed)) {
                Audio.play(Sfx.DENY)
                return null
            }
            return level
        }
        return null
    }

    fun tappedBack(x: Float, y: Float, w: Int, h: Int): Boolean = backButtonRect(w, h).contains(x, y)

    // ---- 設定 ----

    fun drawSettings(
        canvas: Canvas,
        w: Int,
        h: Int,
        soundOn: Boolean,
        musicOn: Boolean,
        languageTag: String,
        resetArmed: Boolean
    ) {
        drawBackground(canvas, w, h)
        Widgets.centered(canvas, Strings.get(R.string.settings_title), w / 2f, h * 0.2f, Ui.dp(24f), bold = true, color = Colors.of("#FFF7D6"))

        val on = Strings.get(R.string.settings_on)
        val off = Strings.get(R.string.settings_off)
        Widgets.button(
            canvas, settingRect(w, h, 0), Strings.get(R.string.settings_sound),
            if (soundOn) Widgets.GREEN_TOP else Widgets.GRAY_TOP,
            if (soundOn) Widgets.GREEN_BOTTOM else Widgets.GRAY_BOTTOM,
            textSize = Ui.dp(13f), subLabel = if (soundOn) on else off
        )
        Widgets.button(
            canvas, settingRect(w, h, 1), Strings.get(R.string.settings_music),
            if (musicOn) Widgets.GREEN_TOP else Widgets.GRAY_TOP,
            if (musicOn) Widgets.GREEN_BOTTOM else Widgets.GRAY_BOTTOM,
            textSize = Ui.dp(13f), subLabel = if (musicOn) on else off
        )
        Widgets.button(
            canvas, settingRect(w, h, 2), Strings.get(R.string.settings_language),
            Widgets.PURPLE_TOP, Widgets.PURPLE_BOTTOM,
            textSize = Ui.dp(13f), subLabel = LocaleManager.displayName(languageTag)
        )
        Widgets.button(
            canvas, settingRect(w, h, 3),
            Strings.get(if (resetArmed) R.string.settings_reset_confirm else R.string.settings_reset),
            Widgets.RED_TOP, Widgets.RED_BOTTOM, textSize = Ui.dp(13f),
            subLabel = if (resetArmed) Strings.get(R.string.settings_reset_warning) else null
        )
        Widgets.button(canvas, settingRect(w, h, 4), Strings.get(R.string.common_back), Widgets.BLUE_TOP, Widgets.BLUE_BOTTOM, textSize = Ui.dp(13f))
    }

    fun settingsTap(x: Float, y: Float, w: Int, h: Int): SettingsAction = when {
        settingRect(w, h, 0).contains(x, y) -> SettingsAction.TOGGLE_SOUND
        settingRect(w, h, 1).contains(x, y) -> SettingsAction.TOGGLE_MUSIC
        settingRect(w, h, 2).contains(x, y) -> SettingsAction.CYCLE_LANGUAGE
        settingRect(w, h, 3).contains(x, y) -> SettingsAction.RESET_PROGRESS
        settingRect(w, h, 4).contains(x, y) -> SettingsAction.BACK
        else -> SettingsAction.NONE
    }

    // ---- 玩法說明 ----

    fun drawHelp(canvas: Canvas, w: Int, h: Int) {
        drawBackground(canvas, w, h)
        Widgets.centered(canvas, Strings.get(R.string.help_title), w / 2f, h * 0.11f, Ui.dp(22f), bold = true, color = Colors.of("#FFF7D6"))

        val colGap = Ui.dp(14f)
        val colW = (w - Ui.dp(28f) - colGap) / 2f
        val leftX = Ui.dp(14f)
        val rightX = leftX + colW + colGap
        val y = h * 0.2f

        Widgets.left(canvas, Strings.get(R.string.help_towers), leftX, y, Ui.dp(13f), Colors.of("#FFE9A8"), bold = true)
        var lineY = y + Ui.dp(15f)
        for (type in TowerType.values()) {
            Widgets.leftFit(
                canvas,
                Strings.format(R.string.help_tower_line, type.emoji, type.displayName, type.baseCost, type.tagline),
                leftX, lineY, Ui.dp(10f), colW, Colors.of("#D5E6D8")
            )
            lineY += Ui.dp(13f)
        }
        lineY += Ui.dp(4f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_tower_note_1), leftX, lineY, Ui.dp(9.5f), colW, Colors.of("#A9C3B0"))
        lineY += Ui.dp(12f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_tower_note_2), leftX, lineY, Ui.dp(9.5f), colW, Colors.of("#A9C3B0"))
        lineY += Ui.dp(12f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_loadout), leftX, lineY, Ui.dp(9.5f), colW, Colors.of("#A9C3B0"))

        Widgets.left(canvas, Strings.get(R.string.help_enemies), rightX, y, Ui.dp(13f), Colors.of("#FFE9A8"), bold = true)
        lineY = y + Ui.dp(15f)
        for (kind in EnemyKind.values()) {
            Widgets.leftFit(
                canvas,
                Strings.format(R.string.help_enemy_line, kind.emoji, kind.displayName, kind.note),
                rightX, lineY, Ui.dp(10f), colW, Colors.of("#D5E6D8")
            )
            lineY += Ui.dp(13f)
        }
        lineY += Ui.dp(4f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_enemy_note_1), rightX, lineY, Ui.dp(9.5f), colW, Colors.of("#A9C3B0"))
        lineY += Ui.dp(12f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_enemy_note_2), rightX, lineY, Ui.dp(9.5f), colW, Colors.of("#A9C3B0"))

        lineY += Ui.dp(18f)
        Widgets.left(canvas, Strings.get(R.string.help_obstacles), rightX, lineY, Ui.dp(13f), Colors.of("#FFE9A8"), bold = true)
        lineY += Ui.dp(14f)
        val props = ObstacleKind.values().joinToString("   ") { "${it.emoji} ${it.displayName}" }
        Widgets.leftFit(canvas, props, rightX, lineY, Ui.dp(9.5f), colW, Colors.of("#D5E6D8"))
        lineY += Ui.dp(12f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_obstacle_note_1), rightX, lineY, Ui.dp(9.5f), colW, Colors.of("#A9C3B0"))
        lineY += Ui.dp(12f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_obstacle_note_2), rightX, lineY, Ui.dp(9.5f), colW, Colors.of("#A9C3B0"))
        lineY += Ui.dp(12f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_obstacle_note_3), rightX, lineY, Ui.dp(9.5f), colW, Colors.of("#A9C3B0"))

        Widgets.button(canvas, backButtonRect(w, h), Strings.get(R.string.common_back), Widgets.GRAY_TOP, Widgets.GRAY_BOTTOM, textSize = Ui.dp(13f))
    }

    // ---- 共用 ----

    private fun drawWrapped(canvas: Canvas, text: String, rect: RectF, size: Float, startY: Float, color: Int) {
        val maxWidth = rect.width() - Ui.dp(12f)
        if (Widgets.measure(text, size) <= maxWidth) {
            Widgets.centered(canvas, text, rect.centerX(), startY, size, color = color)
            return
        }
        var split = text.length / 2
        while (split < text.length && Widgets.measure(text.substring(0, split), size) < maxWidth) split++
        val first = text.substring(0, split.coerceAtMost(text.length))
        val second = text.substring(split.coerceAtMost(text.length))
        Widgets.centered(canvas, first, rect.centerX(), startY, size, color = color)
        if (second.isNotEmpty()) {
            Widgets.centered(canvas, second, rect.centerX(), startY + size * 1.25f, size, color = color)
        }
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int) {
        drawThemedBackground(canvas, w, h, Chapters.default.theme)
        paint.textSize = Ui.dp(24f)
        paint.color = Color.WHITE
        canvas.drawText("🥕", w * 0.08f, h * 0.94f, paint)
        canvas.drawText("🥕", w * 0.88f, h * 0.9f, paint)
    }

    private fun drawThemedBackground(canvas: Canvas, w: Int, h: Int, theme: ChapterTheme) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(theme.skyTopColor, theme.skyMidColor, theme.skyBottomColor),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        paint.color = Colors.of("#26FFFFFF")
        canvas.drawCircle(w * 0.86f, h * 0.16f, Ui.dp(22f), paint)

        paint.color = theme.hillColor
        canvas.drawRoundRect(RectF(-Ui.dp(14f), h * 0.68f, w + Ui.dp(14f), h + Ui.dp(20f)), Ui.dp(20f), Ui.dp(20f), paint)

        paint.color = theme.grassAColor
        var y = h * 0.74f
        while (y < h) {
            canvas.drawRoundRect(RectF(Ui.dp(14f), y, w - Ui.dp(14f), y + Ui.dp(3f)), Ui.dp(3f), Ui.dp(3f), paint)
            y += Ui.dp(18f)
        }
    }

    companion object {

        fun mainItems(hasSave: Boolean): List<MenuAction> = if (hasSave) {
            listOf(MenuAction.CONTINUE, MenuAction.START, MenuAction.LEVELS, MenuAction.HELP, MenuAction.SETTINGS)
        } else {
            listOf(MenuAction.START, MenuAction.LEVELS, MenuAction.HELP, MenuAction.SETTINGS)
        }

        fun mainButtonRect(w: Int, h: Int, index: Int, count: Int): RectF {
            val width = (w * 0.36f).coerceAtMost(Ui.dp(160f))
            val height = Ui.dp(32f)
            val gap = Ui.dp(9f)
            val total = height * count + gap * (count - 1)
            val top = (h - total) / 2f + index * (height + gap)
            val centerX = w * 0.72f
            return RectF(centerX - width / 2f, top, centerX + width / 2f, top + height)
        }

        fun chapterCardRect(w: Int, h: Int, index: Int): RectF {
            val count = Chapters.all.size
            val gap = Ui.dp(9f)
            val available = w - Ui.dp(24f) - gap * (count - 1)
            val width = (available / count).coerceAtMost(Ui.dp(132f))
            val height = Ui.dp(150f)
            val total = width * count + gap * (count - 1)
            val left = (w - total) / 2f + index * (width + gap)
            val top = h * 0.21f
            return RectF(left, top, left + width, top + height)
        }

        fun levelNodeRadius(): Float = Ui.dp(19f)

        /**
         * 關卡節點沿著一條起伏的小徑排開：整體往右上推進，
         * 中間夾一個波浪，讓「一路走過來」的感覺比一排方塊明顯。
         */
        fun levelNodeCenter(w: Int, h: Int, index: Int, count: Int): Pair<Float, Float> {
            val steps = (count - 1).coerceAtLeast(1)
            val t = index.toFloat() / steps
            val marginX = w * 0.13f
            val x = marginX + (w - marginX * 2f) * t
            val y = h * 0.60f - t * h * 0.10f + sin(t * (PI * 2f).toFloat()) * h * 0.11f
            return Pair(x, y)
        }

        /** 設定頁列數：音效、音樂、語言、重置、返回。 */
        private const val SETTINGS_ROWS = 5

        fun settingRect(w: Int, h: Int, index: Int): RectF {
            val width = (w * 0.4f).coerceAtMost(Ui.dp(176f))
            val height = Ui.dp(34f)
            val gap = Ui.dp(10f)
            val count = SETTINGS_ROWS
            val total = height * count + gap * (count - 1)
            val top = h * 0.5f - total / 2f + Ui.dp(24f) + index * (height + gap)
            return RectF((w - width) / 2f, top, (w + width) / 2f, top + height)
        }

        fun backButtonRect(w: Int, h: Int): RectF {
            val width = Ui.dp(80f)
            val height = Ui.dp(30f)
            return RectF(Ui.dp(12f), h - height - Ui.dp(10f), Ui.dp(12f) + width, h - Ui.dp(10f))
        }
    }
}
