package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

class HudRenderer {

    private val paint = Paint().apply { isAntiAlias = true }

    // ---- 遊戲中 HUD ----

    fun draw(canvas: Canvas, w: Int, h: Int, enemyManager: EnemyManager) {
        drawTopBar(canvas, w, enemyManager)
        drawBossBar(canvas, w, enemyManager)
        if (GameState.status == GameStatus.PLAYING) {
            drawWaveCall(canvas, w, h, enemyManager)
        }
    }

    private fun drawTopBar(canvas: Canvas, w: Int, enemyManager: EnemyManager) {
        val barH = Ui.topBarHeight
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, barH,
            Color.parseColor("#F21A2A2B"), Color.parseColor("#D2131D20"), Shader.TileMode.CLAMP
        )
        canvas.drawRect(RectF(0f, 0f, w.toFloat(), barH), paint)
        paint.shader = null
        paint.color = Color.parseColor("#445FE36B")
        canvas.drawRect(RectF(0f, barH - Ui.dp(1.5f), w.toFloat(), barH), paint)

        val pillW = Ui.dp(74f)
        val pillH = Ui.dp(26f)
        val pillTop = (barH - pillH) / 2f
        val carrotDanger = GameState.carrotHp * 3 <= GameState.maxCarrotHp
        drawPill(
            canvas, RectF(Ui.dp(8f), pillTop, Ui.dp(8f) + pillW, pillTop + pillH),
            "🥕", "${GameState.carrotHp}/${GameState.maxCarrotHp}",
            if (carrotDanger) "#EF4444" else "#D96031"
        )
        val goldLeft = Ui.dp(8f) + pillW + Ui.dp(6f)
        drawPill(
            canvas, RectF(goldLeft, pillTop, goldLeft + pillW, pillTop + pillH),
            "🪙", GameState.gold.toString(), "#D7A331"
        )

        val speedRect = speedButtonRect(w)
        val pauseRect = pauseButtonRect(w)

        // 關卡與波次
        val infoLeft = goldLeft + pillW + Ui.dp(10f)
        val infoRight = speedRect.left - Ui.dp(8f)
        if (infoRight - infoLeft > Ui.dp(60f)) {
            val title = GameState.level.name
            Widgets.left(canvas, title, infoLeft, barH * 0.44f, Ui.dp(12.5f), Color.parseColor("#FFF3D0"), bold = true)
            val waveText = Strings.format(
                R.string.hud_wave, enemyManager.currentWaveNumber, enemyManager.totalWaves
            )
            val remaining = enemyManager.enemies.size + enemyManager.pendingInWave
            val sub = if (remaining > 0) {
                Strings.format(R.string.hud_wave_remaining, waveText, remaining)
            } else {
                waveText
            }
            Widgets.left(canvas, sub, infoLeft, barH * 0.78f, Ui.dp(10f), Color.parseColor("#B8D6CE"))

            val scoreText = Strings.format(R.string.hud_score, GameState.score)
            val scoreW = Widgets.measure(scoreText, Ui.dp(10.5f))
            if (infoRight - infoLeft > scoreW + Widgets.measure(sub, Ui.dp(10f)) + Ui.dp(20f)) {
                Widgets.left(canvas, scoreText, infoRight - scoreW, barH * 0.78f, Ui.dp(10.5f), Color.parseColor("#9FBEB6"))
            }
        }

        // 速度切換
        Widgets.button(
            canvas, speedRect, "${GameState.speed}x",
            topColor = if (GameState.speed > 1) Widgets.AMBER_TOP else "#2E3B38",
            bottomColor = if (GameState.speed > 1) Widgets.AMBER_BOTTOM else "#1E2926",
            textSize = Ui.dp(13f)
        )
        // 暫停
        Widgets.button(
            canvas, pauseRect,
            if (GameState.status == GameStatus.PAUSED) "▶" else "❚❚",
            topColor = "#2E3B38", bottomColor = "#1E2926", textSize = Ui.dp(12f)
        )
    }

    private fun drawPill(canvas: Canvas, rect: RectF, icon: String, value: String, accent: String) {
        val radius = rect.height() / 2f
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.parseColor("#66000000")
        canvas.drawRoundRect(RectF(rect.left, rect.top + Ui.dp(2f), rect.right, rect.bottom + Ui.dp(2f)), radius, radius, paint)
        paint.color = Color.parseColor("#E6212C29")
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = Color.parseColor(accent)
        canvas.drawCircle(rect.left + radius, rect.centerY(), radius * 0.82f, paint)

        paint.textSize = rect.height() * 0.66f
        canvas.drawText(icon, rect.left + radius - paint.measureText(icon) / 2f, rect.centerY() + rect.height() * 0.24f, paint)

        Widgets.left(
            canvas, value, rect.left + radius * 2f + Ui.dp(2f),
            rect.centerY() + rect.height() * 0.22f, rect.height() * 0.52f,
            Color.parseColor("#FFFDF2"), bold = true
        )
    }

    private fun drawBossBar(canvas: Canvas, w: Int, enemyManager: EnemyManager) {
        val boss = enemyManager.activeBoss() ?: return
        val barW = (w * 0.5f).coerceAtMost(Ui.dp(260f))
        val barH = Ui.dp(11f)
        val left = (w - barW) / 2f
        val top = Ui.topBarHeight + Ui.dp(6f)

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.parseColor("#99000000")
        canvas.drawRoundRect(RectF(left - Ui.dp(2f), top - Ui.dp(2f), left + barW + Ui.dp(2f), top + barH + Ui.dp(2f)), barH, barH, paint)
        paint.color = Color.parseColor("#3A1A1A")
        canvas.drawRoundRect(RectF(left, top, left + barW, top + barH), barH, barH, paint)
        paint.color = Color.parseColor("#EF4444")
        canvas.drawRoundRect(RectF(left, top, left + barW * boss.hpRatio, top + barH), barH, barH, paint)

        Widgets.centered(
            canvas,
            Strings.format(R.string.hud_boss, boss.kind.emoji, boss.kind.displayName, boss.hp),
            w / 2f, top + barH * 0.82f, Ui.dp(8.5f), bold = true, color = Color.parseColor("#FFECEC")
        )
    }

    private fun drawWaveCall(canvas: Canvas, w: Int, h: Int, enemyManager: EnemyManager) {
        val rect = callWaveRect(w, h)
        val preview = enemyManager.nextWavePreview()

        if (enemyManager.canCallNextWave() && preview != null) {
            // 預覽下一波組成
            val chipY = rect.top - Ui.dp(7f)
            var chipX = rect.left
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.textSize = Ui.dp(13f)
            for (kind in preview.kinds) {
                canvas.drawText(kind.emoji, chipX, chipY, paint)
                chipX += Ui.dp(16f)
            }
            if (preview.hasBoss()) {
                Widgets.left(canvas, "BOSS", chipX + Ui.dp(2f), chipY, Ui.dp(9f), Color.parseColor("#FF9A9A"), bold = true)
            }

            Widgets.button(
                canvas, rect, Strings.get(R.string.hud_call_wave), Widgets.AMBER_TOP, Widgets.AMBER_BOTTOM,
                textSize = Ui.dp(12f),
                subLabel = Strings.format(
                    R.string.hud_call_wave_bonus, enemyManager.callBonus, enemyManager.countdownSeconds
                )
            )
        } else {
            Widgets.button(
                canvas, rect, Strings.get(R.string.hud_incoming), "#2A3532", "#1A2320",
                enabled = false, textSize = Ui.dp(12f),
                subLabel = Strings.format(
                    R.string.hud_remaining, enemyManager.enemies.size + enemyManager.pendingInWave
                )
            )
        }
    }

    // ---- 覆蓋層 ----

    fun drawPauseOverlay(canvas: Canvas, w: Int, h: Int) {
        Widgets.scrim(canvas, w, h, "#D40B1112")
        Widgets.centered(canvas, Strings.get(R.string.pause_title), w / 2f, h * 0.26f, Ui.dp(30f), bold = true, color = Color.parseColor("#FFF7D6"))
        Widgets.centered(
            canvas,
            Strings.format(R.string.pause_summary, GameState.level.name, GameState.wave, GameState.score),
            w / 2f, h * 0.26f + Ui.dp(24f), Ui.dp(12f), color = Color.parseColor("#BFD3C9")
        )

        Widgets.button(canvas, rowButtonRect(w, h, 0, 4, 0.55f), Strings.get(R.string.pause_resume), Widgets.GREEN_TOP, Widgets.GREEN_BOTTOM, textSize = Ui.dp(13f))
        Widgets.button(canvas, rowButtonRect(w, h, 1, 4, 0.55f), Strings.get(R.string.pause_restart), Widgets.RED_TOP, Widgets.RED_BOTTOM, textSize = Ui.dp(13f))
        Widgets.button(canvas, rowButtonRect(w, h, 2, 4, 0.55f), Strings.get(R.string.pause_settings), Widgets.PURPLE_TOP, Widgets.PURPLE_BOTTOM, textSize = Ui.dp(13f))
        Widgets.button(canvas, rowButtonRect(w, h, 3, 4, 0.55f), Strings.get(R.string.pause_save_exit), Widgets.BLUE_TOP, Widgets.BLUE_BOTTOM, textSize = Ui.dp(13f))
    }

    fun drawResultOverlay(
        canvas: Canvas,
        w: Int,
        h: Int,
        victory: Boolean,
        stars: Int,
        bestStars: Int,
        hasNextLevel: Boolean
    ) {
        Widgets.scrim(canvas, w, h, if (victory) "#D80D2415" else "#D8180F12")

        val title = Strings.get(if (victory) R.string.result_victory else R.string.result_defeat)
        val titleColor = if (victory) Color.parseColor("#7BE88C") else Color.parseColor("#FF7B7B")
        Widgets.centered(canvas, title, w / 2f, h * 0.24f, Ui.dp(32f), bold = true, color = titleColor)

        if (victory) {
            Widgets.stars(canvas, w / 2f, h * 0.24f + Ui.dp(40f), stars, Ui.dp(30f))
            val note = if (stars > bestStars) {
                Strings.get(R.string.result_new_record)
            } else {
                Strings.format(R.string.result_best, bestStars)
            }
            Widgets.centered(canvas, note, w / 2f, h * 0.24f + Ui.dp(62f), Ui.dp(11f), color = Color.parseColor("#DCE9D9"))
        } else {
            Widgets.centered(
                canvas,
                Strings.format(R.string.result_survived, GameState.wave, GameState.level.waves.size),
                w / 2f, h * 0.24f + Ui.dp(30f), Ui.dp(13f), color = Color.parseColor("#E7C9C9")
            )
        }

        val statsY = h * 0.24f + Ui.dp(if (victory) 88f else 60f)
        val stats = Strings.format(
            R.string.result_stats,
            GameState.kills, GameState.goldEarned, GameState.leaks, GameState.score
        )
        Widgets.centered(canvas, stats, w / 2f, statsY, Ui.dp(11.5f), color = Color.parseColor("#C7D8CF"))

        val count = if (victory && hasNextLevel) 3 else 2
        var index = 0
        if (victory && hasNextLevel) {
            Widgets.button(canvas, resultButtonRect(w, h, index, count), Strings.get(R.string.result_next_level), Widgets.GREEN_TOP, Widgets.GREEN_BOTTOM, textSize = Ui.dp(13f))
            index++
        }
        Widgets.button(canvas, resultButtonRect(w, h, index, count), Strings.get(R.string.result_replay), Widgets.AMBER_TOP, Widgets.AMBER_BOTTOM, textSize = Ui.dp(13f))
        index++
        Widgets.button(canvas, resultButtonRect(w, h, index, count), Strings.get(R.string.result_menu), Widgets.BLUE_TOP, Widgets.BLUE_BOTTOM, textSize = Ui.dp(13f))
    }

    companion object {

        fun pauseButtonRect(w: Int): RectF {
            val size = Ui.dp(30f)
            val right = w - Ui.dp(8f)
            val top = (Ui.topBarHeight - size) / 2f
            return RectF(right - size, top, right, top + size)
        }

        fun speedButtonRect(w: Int): RectF {
            val height = Ui.dp(30f)
            val width = Ui.dp(40f)
            val right = pauseButtonRect(w).left - Ui.dp(6f)
            val top = (Ui.topBarHeight - height) / 2f
            return RectF(right - width, top, right, top + height)
        }

        fun callWaveRect(w: Int, h: Int): RectF {
            val width = Ui.dp(100f)
            val height = Ui.dp(34f)
            val right = w - Ui.dp(10f)
            val bottom = h - Ui.bottomBarHeight - Ui.dp(8f)
            return RectF(right - width, bottom - height, right, bottom)
        }

        /** 覆蓋層上水平排列的按鈕。 */
        fun rowButtonRect(w: Int, h: Int, index: Int, count: Int, topRatio: Float): RectF {
            val maxWidth = (w - Ui.dp(30f)) / count - Ui.dp(8f)
            val width = maxWidth.coerceAtMost(Ui.dp(108f)).coerceAtLeast(Ui.dp(52f))
            val height = Ui.dp(34f)
            val gap = Ui.dp(8f)
            val total = width * count + gap * (count - 1)
            val left = (w - total) / 2f + index * (width + gap)
            val top = h * topRatio
            return RectF(left, top, left + width, top + height)
        }

        fun resultButtonRect(w: Int, h: Int, index: Int, count: Int): RectF =
            rowButtonRect(w, h, index, count, 0.7f)
    }
}
