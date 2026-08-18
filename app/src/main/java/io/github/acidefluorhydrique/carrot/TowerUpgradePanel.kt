// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.util.Locale

/**
 * 選中塔之後的操作面板：升級、賣出、切換選敵模式。
 * 放在左下角，避開右下角的「催下一波」按鈕與底部選塔列。
 */
class TowerUpgradePanel {

    private val paint = Paint().apply { isAntiAlias = true }

    /** 回傳 true 表示點擊被面板吃掉。 */
    fun onTap(x: Float, y: Float, w: Int, h: Int, towerManager: TowerManager): Boolean {
        if (towerManager.selectedTower == null) return false
        if (!panelRect(w, h).contains(x, y)) return false

        when {
            closeRect(w, h).contains(x, y) -> towerManager.clearSelection()
            upgradeRect(w, h).contains(x, y) -> towerManager.upgradeSelected()
            sellRect(w, h).contains(x, y) -> towerManager.sellSelected()
            modeRect(w, h).contains(x, y) -> towerManager.cycleTargetModeOfSelected()
            else -> Unit
        }
        return true
    }

    fun draw(canvas: Canvas, w: Int, h: Int, towerManager: TowerManager) {
        val tower = towerManager.selectedTower ?: return
        val panel = panelRect(w, h)
        Widgets.panel(canvas, panel)

        val padding = Ui.dp(8f)
        val textWidth = panel.width() - padding * 2f - Ui.dp(22f)
        val header = Strings.format(
            R.string.tower_panel_header, tower.type.emoji, tower.type.displayName, tower.level
        )
        Widgets.leftFit(
            canvas, header, panel.left + padding, panel.top + Ui.dp(15f),
            Ui.dp(13f), textWidth, Color.parseColor("#FFFDF2"), bold = true
        )

        val close = closeRect(w, h)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#33FFFFFF")
        canvas.drawRoundRect(close, Ui.dp(5f), Ui.dp(5f), paint)
        Widgets.centered(canvas, "✕", close.centerX(), close.centerY() + Ui.dp(4f), Ui.dp(11f), bold = true, color = Color.parseColor("#F0F5EE"))

        val statsColor = Color.parseColor("#C9DED2")
        Widgets.leftFit(
            canvas,
            Strings.format(
                R.string.tower_panel_stats,
                tower.damage, tower.dps, (tower.range / Ui.dp(1f)).toInt()
            ),
            panel.left + padding, panel.top + Ui.dp(29f), Ui.dp(9.5f),
            panel.width() - padding * 2f, statsColor
        )
        Widgets.leftFit(
            canvas, extraStat(tower),
            panel.left + padding, panel.top + Ui.dp(40f), Ui.dp(9.5f),
            panel.width() - padding * 2f, statsColor
        )

        val canUpgrade = towerManager.canUpgradeSelected()
        Widgets.button(
            canvas, upgradeRect(w, h),
            Strings.get(if (tower.isMaxLevel) R.string.tower_max_level else R.string.tower_upgrade),
            Widgets.GREEN_TOP, Widgets.GREEN_BOTTOM,
            enabled = canUpgrade,
            textSize = Ui.dp(10.5f),
            subLabel = if (tower.isMaxLevel) null else Strings.format(R.string.tower_price, tower.upgradeCost)
        )
        Widgets.button(
            canvas, sellRect(w, h), Strings.get(R.string.tower_sell), Widgets.RED_TOP, Widgets.RED_BOTTOM,
            textSize = Ui.dp(10.5f), subLabel = Strings.format(R.string.tower_refund, tower.sellValue)
        )
        Widgets.button(
            canvas, modeRect(w, h), Strings.get(R.string.tower_target), Widgets.BLUE_TOP, Widgets.BLUE_BOTTOM,
            textSize = Ui.dp(10.5f), subLabel = tower.targetMode.label
        )
    }

    private fun extraStat(tower: Tower): String = when (tower.type) {
        TowerType.ARROW -> Strings.format(
            R.string.tower_stat_rate,
            String.format(Locale.US, "%.1f", 60f / tower.attackInterval)
        )
        TowerType.ICE -> Strings.format(
            R.string.tower_stat_slow,
            ((1f - tower.slowFactor) * 100).toInt(), tower.slowDuration / 60
        )
        TowerType.BOMB -> Strings.format(
            R.string.tower_stat_splash, (tower.splashRadius / Ui.dp(1f)).toInt()
        )
        TowerType.POISON -> Strings.format(R.string.tower_stat_poison, tower.poisonDamage)
        TowerType.LIGHT -> Strings.format(R.string.tower_stat_chain, tower.chainTargets)
        TowerType.SUN -> Strings.format(R.string.tower_stat_pulse, (tower.range / Ui.dp(1f)).toInt())
        TowerType.MOON -> Strings.format(
            R.string.tower_stat_aura,
            ((1f - tower.auraSlowFactor) * 100).toInt(), (tower.range / Ui.dp(1f)).toInt()
        )
        TowerType.ROCKET -> Strings.format(R.string.tower_stat_pierce, (tower.range / Ui.dp(1f)).toInt())
    }

    companion object {

        fun panelRect(w: Int, h: Int): RectF {
            val width = (w * 0.46f).coerceAtMost(Ui.dp(206f))
            val height = Ui.dp(94f)
            val left = Ui.dp(10f)
            val bottom = h - Ui.bottomBarHeight - Ui.dp(8f)
            return RectF(left, bottom - height, left + width, bottom)
        }

        fun closeRect(w: Int, h: Int): RectF {
            val panel = panelRect(w, h)
            val size = Ui.dp(18f)
            return RectF(panel.right - size - Ui.dp(6f), panel.top + Ui.dp(5f), panel.right - Ui.dp(6f), panel.top + Ui.dp(5f) + size)
        }

        fun upgradeRect(w: Int, h: Int): RectF = actionRect(w, h, 0)

        fun sellRect(w: Int, h: Int): RectF = actionRect(w, h, 1)

        fun modeRect(w: Int, h: Int): RectF = actionRect(w, h, 2)

        private fun actionRect(w: Int, h: Int, index: Int): RectF {
            val panel = panelRect(w, h)
            val padding = Ui.dp(8f)
            val gap = Ui.dp(6f)
            val width = (panel.width() - padding * 2 - gap * 2) / 3f
            val height = Ui.dp(30f)
            val left = panel.left + padding + index * (width + gap)
            val top = panel.bottom - padding - height
            return RectF(left, top, left + width, top + height)
        }
    }
}
