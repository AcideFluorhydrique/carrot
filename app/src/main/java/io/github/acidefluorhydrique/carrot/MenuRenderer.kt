package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

enum class MenuAction { NONE, CONTINUE, START, LEVELS, HELP, SETTINGS, BACK }

enum class SettingsAction { NONE, TOGGLE_SOUND, TOGGLE_MUSIC, RESET_PROGRESS, BACK }

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
            Ui.dp(34f), titleWidth, bold = true, color = Color.parseColor("#FFF7D6")
        )
        Widgets.centeredFit(
            canvas, Strings.get(R.string.menu_tagline), leftX, h * 0.3f + Ui.dp(22f),
            Ui.dp(11.5f), titleWidth, color = Color.parseColor("#D3E4CC")
        )

        val bob = sin(frame * 0.05f) * Ui.dp(4f)
        paint.style = Paint.Style.FILL
        paint.textSize = Ui.dp(56f)
        val carrot = "🥕"
        canvas.drawText(carrot, leftX - paint.measureText(carrot) / 2f, h * 0.62f + bob, paint)

        Widgets.centered(
            canvas,
            Strings.format(
                R.string.menu_progress,
                completedCount, GameLevels.all.size, totalStars, GameLevels.all.size * 3
            ),
            leftX, h * 0.76f, Ui.dp(11f), color = Color.parseColor("#C4D9C8")
        )
        Widgets.centered(
            canvas, Strings.format(R.string.menu_current_level, currentLevel.name),
            leftX, h * 0.83f, Ui.dp(10.5f), color = Color.parseColor("#9FBDA6")
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

    // ---- 選關 ----

    fun drawLevels(canvas: Canvas, w: Int, h: Int, starsOf: (Int) -> Int, completed: Set<Int>) {
        drawBackground(canvas, w, h)
        Widgets.centered(canvas, Strings.get(R.string.levels_title), w / 2f, h * 0.13f, Ui.dp(24f), bold = true, color = Color.parseColor("#FFF7D6"))

        for (i in GameLevels.all.indices) {
            val level = GameLevels.all[i]
            val rect = levelCardRect(w, h, i)
            val unlocked = GameLevels.isUnlocked(level.id, completed)
            val stars = starsOf(level.id)
            val selected = level.id == GameState.level.id

            Widgets.panel(
                canvas, rect,
                radius = Ui.dp(10f),
                topColor = when {
                    !unlocked -> "#CC1B2320"
                    selected -> "#EE38663F"
                    else -> "#E61E2E33"
                },
                bottomColor = when {
                    !unlocked -> "#CC121815"
                    selected -> "#E6234A2B"
                    else -> "#DE14212A"
                },
                borderColor = if (selected) "#CCEFFFBA" else "#44FFFFFF"
            )

            if (!unlocked) {
                Widgets.centered(canvas, "🔒", rect.centerX(), rect.centerY(), Ui.dp(26f), color = Color.WHITE)
                Widgets.centeredFit(
                    canvas, Strings.format(R.string.level_locked, i), rect.centerX(), rect.centerY() + Ui.dp(20f),
                    Ui.dp(9f), rect.width() - Ui.dp(10f), color = Color.parseColor("#A9B6AE")
                )
                continue
            }

            Widgets.centered(
                canvas, "${level.id}", rect.centerX(), rect.top + Ui.dp(24f),
                Ui.dp(20f), bold = true, color = Color.parseColor("#FFE9A8")
            )
            Widgets.centeredFit(
                canvas, level.name, rect.centerX(), rect.top + Ui.dp(42f),
                Ui.dp(12.5f), rect.width() - Ui.dp(10f), bold = true, color = Color.parseColor("#FFFDF2")
            )
            drawWrapped(canvas, level.subtitle, rect, Ui.dp(9f), rect.top + Ui.dp(57f), Color.parseColor("#B9CCC0"))

            Widgets.stars(canvas, rect.centerX(), rect.bottom - Ui.dp(24f), stars, Ui.dp(14f))
            Widgets.centeredFit(
                canvas, Strings.format(R.string.level_card_info, level.waves.size, level.carrotHp),
                rect.centerX(), rect.bottom - Ui.dp(8f), Ui.dp(9f), rect.width() - Ui.dp(10f),
                color = Color.parseColor("#9FBDA6")
            )
        }

        Widgets.button(canvas, backButtonRect(w, h), Strings.get(R.string.common_back), Widgets.GRAY_TOP, Widgets.GRAY_BOTTOM, textSize = Ui.dp(13f))
    }

    /** 回傳被點到的關卡；點到未解鎖的關卡回傳 null。 */
    fun levelTap(x: Float, y: Float, w: Int, h: Int, completed: Set<Int>): LevelConfig? {
        for (i in GameLevels.all.indices) {
            if (!levelCardRect(w, h, i).contains(x, y)) continue
            val level = GameLevels.all[i]
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

    fun drawSettings(canvas: Canvas, w: Int, h: Int, soundOn: Boolean, musicOn: Boolean, resetArmed: Boolean) {
        drawBackground(canvas, w, h)
        Widgets.centered(canvas, Strings.get(R.string.settings_title), w / 2f, h * 0.2f, Ui.dp(24f), bold = true, color = Color.parseColor("#FFF7D6"))

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
            canvas, settingRect(w, h, 2),
            Strings.get(if (resetArmed) R.string.settings_reset_confirm else R.string.settings_reset),
            Widgets.RED_TOP, Widgets.RED_BOTTOM, textSize = Ui.dp(13f),
            subLabel = if (resetArmed) Strings.get(R.string.settings_reset_warning) else null
        )
        Widgets.button(canvas, settingRect(w, h, 3), Strings.get(R.string.common_back), Widgets.BLUE_TOP, Widgets.BLUE_BOTTOM, textSize = Ui.dp(13f))
    }

    fun settingsTap(x: Float, y: Float, w: Int, h: Int): SettingsAction = when {
        settingRect(w, h, 0).contains(x, y) -> SettingsAction.TOGGLE_SOUND
        settingRect(w, h, 1).contains(x, y) -> SettingsAction.TOGGLE_MUSIC
        settingRect(w, h, 2).contains(x, y) -> SettingsAction.RESET_PROGRESS
        settingRect(w, h, 3).contains(x, y) -> SettingsAction.BACK
        else -> SettingsAction.NONE
    }

    // ---- 玩法說明 ----

    fun drawHelp(canvas: Canvas, w: Int, h: Int) {
        drawBackground(canvas, w, h)
        Widgets.centered(canvas, Strings.get(R.string.help_title), w / 2f, h * 0.11f, Ui.dp(22f), bold = true, color = Color.parseColor("#FFF7D6"))

        val colGap = Ui.dp(14f)
        val colW = (w - Ui.dp(28f) - colGap) / 2f
        val leftX = Ui.dp(14f)
        val rightX = leftX + colW + colGap
        val y = h * 0.2f

        Widgets.left(canvas, Strings.get(R.string.help_towers), leftX, y, Ui.dp(13f), Color.parseColor("#FFE9A8"), bold = true)
        var lineY = y + Ui.dp(15f)
        for (type in TowerType.values()) {
            Widgets.leftFit(
                canvas,
                Strings.format(R.string.help_tower_line, type.emoji, type.displayName, type.baseCost, type.tagline),
                leftX, lineY, Ui.dp(10f), colW, Color.parseColor("#D5E6D8")
            )
            lineY += Ui.dp(13f)
        }
        lineY += Ui.dp(4f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_tower_note_1), leftX, lineY, Ui.dp(9.5f), colW, Color.parseColor("#A9C3B0"))
        lineY += Ui.dp(12f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_tower_note_2), leftX, lineY, Ui.dp(9.5f), colW, Color.parseColor("#A9C3B0"))

        Widgets.left(canvas, Strings.get(R.string.help_enemies), rightX, y, Ui.dp(13f), Color.parseColor("#FFE9A8"), bold = true)
        lineY = y + Ui.dp(15f)
        for (kind in EnemyKind.values()) {
            Widgets.leftFit(
                canvas,
                Strings.format(R.string.help_enemy_line, kind.emoji, kind.displayName, kind.note),
                rightX, lineY, Ui.dp(10f), colW, Color.parseColor("#D5E6D8")
            )
            lineY += Ui.dp(13f)
        }
        lineY += Ui.dp(4f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_enemy_note_1), rightX, lineY, Ui.dp(9.5f), colW, Color.parseColor("#A9C3B0"))
        lineY += Ui.dp(12f)
        Widgets.leftFit(canvas, Strings.get(R.string.help_enemy_note_2), rightX, lineY, Ui.dp(9.5f), colW, Color.parseColor("#A9C3B0"))

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
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.parseColor("#152A31"),
                Color.parseColor("#1E3A2B"),
                Color.parseColor("#3B622D")
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        paint.color = Color.parseColor("#33FFFFFF")
        canvas.drawCircle(w * 0.86f, h * 0.16f, Ui.dp(22f), paint)

        paint.color = Color.parseColor("#2B5531")
        canvas.drawRoundRect(RectF(-Ui.dp(14f), h * 0.66f, w + Ui.dp(14f), h + Ui.dp(20f)), Ui.dp(20f), Ui.dp(20f), paint)
        paint.color = Color.parseColor("#7E5F2A")
        var y = h * 0.72f
        while (y < h) {
            canvas.drawRoundRect(RectF(Ui.dp(14f), y, w - Ui.dp(14f), y + Ui.dp(3f)), Ui.dp(3f), Ui.dp(3f), paint)
            y += Ui.dp(18f)
        }

        paint.textSize = Ui.dp(24f)
        canvas.drawText("🥕", w * 0.08f, h * 0.94f, paint)
        canvas.drawText("🥕", w * 0.88f, h * 0.9f, paint)
        canvas.drawText("🌿", w * 0.5f, h * 0.97f, paint)
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

        fun levelCardRect(w: Int, h: Int, index: Int): RectF {
            val count = GameLevels.all.size
            val gap = Ui.dp(9f)
            val available = w - Ui.dp(24f) - gap * (count - 1)
            val width = (available / count).coerceAtMost(Ui.dp(132f))
            val height = Ui.dp(150f)
            val total = width * count + gap * (count - 1)
            val left = (w - total) / 2f + index * (width + gap)
            val top = h * 0.21f
            return RectF(left, top, left + width, top + height)
        }

        fun settingRect(w: Int, h: Int, index: Int): RectF {
            val width = (w * 0.4f).coerceAtMost(Ui.dp(176f))
            val height = Ui.dp(34f)
            val gap = Ui.dp(10f)
            val count = 4
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
