package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

class TowerUpgradePanel(private val screenWidth: Int, private val screenHeight: Int) {

    private val paint = Paint().apply { isAntiAlias = true }
    private val panel = RectF()
    private val upgradeButton = RectF()

    init {
        val panelW = minOf(360f, screenWidth - 24f)
        val panelH = 116f
        val bottomBarHeight = 102f
        panel.set(
            screenWidth - panelW - 12f,
            screenHeight - bottomBarHeight - panelH - 12f,
            screenWidth - 12f,
            screenHeight - bottomBarHeight - 12f
        )
        upgradeButton.set(panel.right - 134f, panel.top + 58f, panel.right - 16f, panel.bottom - 14f)
    }

    fun onTap(x: Float, y: Float, towerManager: TowerManager): Boolean {
        if (towerManager.selectedTower == null || !panel.contains(x, y)) return false
        if (upgradeButton.contains(x, y)) {
            towerManager.upgradeSelected()
        }
        return true
    }

    fun draw(canvas: Canvas, towerManager: TowerManager) {
        val tower = towerManager.selectedTower ?: return

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#66000000")
        canvas.drawRoundRect(RectF(panel.left, panel.top + 4f, panel.right, panel.bottom + 4f), 14f, 14f, paint)

        paint.shader = LinearGradient(
            panel.left, panel.top, panel.left, panel.bottom,
            Color.parseColor("#F02B3534"),
            Color.parseColor("#E9182422"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(panel, 10f, 10f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#66FFFFFF")
        canvas.drawRoundRect(panel, 10f, 10f, paint)

        paint.style = Paint.Style.FILL
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#FFFDF2")
        paint.textSize = 28f
        canvas.drawText("${towerIcon(tower.type)} Lv.${tower.level}", panel.left + 16f, panel.top + 34f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 20f
        paint.color = Color.parseColor("#DDE7F5E9")
        canvas.drawText("攻擊 ${tower.damage}  範圍 ${tower.range.toInt()}  間隔 ${tower.attackInterval}", panel.left + 16f, panel.top + 66f, paint)

        val maxLevel = tower.level >= 3
        val canUpgrade = towerManager.canUpgradeSelected()
        paint.style = Paint.Style.FILL
        paint.color = when {
            maxLevel -> Color.parseColor("#55444444")
            canUpgrade -> Color.parseColor("#CC2E7D32")
            else -> Color.parseColor("#66555555")
        }
        canvas.drawRoundRect(upgradeButton, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = if (canUpgrade) Color.parseColor("#99F8FFB6") else Color.parseColor("#33FFFFFF")
        canvas.drawRoundRect(upgradeButton, 8f, 8f, paint)

        paint.style = Paint.Style.FILL
        paint.color = if (maxLevel || canUpgrade) Color.parseColor("#FFFDF2") else Color.parseColor("#99FFFFFF")
        paint.textSize = 22f
        paint.isFakeBoldText = true
        val label = when {
            maxLevel -> "MAX"
            else -> "升級 ${tower.upgradeCost}🪙"
        }
        val tw = paint.measureText(label)
        canvas.drawText(label, upgradeButton.centerX() - tw / 2f, upgradeButton.centerY() + 8f, paint)
        paint.isFakeBoldText = false
    }

    private fun towerIcon(type: TowerType): String {
        return when (type) {
            TowerType.ARROW -> "🏹"
            TowerType.BOMB -> "💣"
            TowerType.ICE -> "❄️"
        }
    }
}
