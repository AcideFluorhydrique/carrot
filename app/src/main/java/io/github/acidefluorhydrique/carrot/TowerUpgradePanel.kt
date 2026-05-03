package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class TowerUpgradePanel(private val screenWidth: Int, private val screenHeight: Int) {

    private val paint = Paint().apply { isAntiAlias = true }
    private val panel = RectF()
    private val upgradeButton = RectF()

    init {
        val panelW = minOf(360f, screenWidth - 24f)
        val panelH = 108f
        panel.set(
            screenWidth - panelW - 12f,
            screenHeight - 90f - panelH - 12f,
            screenWidth - 12f,
            screenHeight - 90f - 12f
        )
        upgradeButton.set(panel.right - 132f, panel.top + 52f, panel.right - 16f, panel.bottom - 14f)
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
        paint.color = Color.parseColor("#CC101018")
        canvas.drawRoundRect(panel, 10f, 10f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#88FFFFFF")
        canvas.drawRoundRect(panel, 10f, 10f, paint)

        paint.style = Paint.Style.FILL
        paint.isFakeBoldText = true
        paint.color = Color.WHITE
        paint.textSize = 28f
        canvas.drawText("${towerIcon(tower.type)} Lv.${tower.level}", panel.left + 16f, panel.top + 34f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 20f
        paint.color = Color.parseColor("#DDFFFFFF")
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

        paint.color = if (maxLevel || canUpgrade) Color.WHITE else Color.parseColor("#99FFFFFF")
        paint.textSize = 22f
        paint.isFakeBoldText = true
        val label = when {
            maxLevel -> "MAX"
            else -> "升級 ${tower.upgradeCost}"
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
