package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.*

enum class TowerType { ARROW, BOMB, ICE }

class Tower(
    val col: Int,
    val row: Int,
    val type: TowerType,
    private val gameMap: GameMap
) {
    var level: Int = 1          // 1~3級
    var cooldown: Int = 0       // 當前冷卻計數

    // 各塔各級屬性
    val range: Float get() = gameMap.cellSize * when (type) {
        TowerType.ARROW -> 2.5f + level * 0.3f
        TowerType.BOMB  -> 2.0f + level * 0.3f
        TowerType.ICE   -> 2.2f + level * 0.3f
    }

    val damage: Int get() = when (type) {
        TowerType.ARROW -> level * 2
        TowerType.BOMB  -> level * 4
        TowerType.ICE   -> 1               // 冰塔極低傷害，固定1
    }

    val attackInterval: Int get() = when (type) {
        TowerType.ARROW -> max(20, 40 - level * 5)
        TowerType.BOMB  -> max(50, 80 - level * 10)
        TowerType.ICE   -> max(30, 55 - level * 8)
    }

    val upgradeCost: Int get() = when (type) {
        TowerType.ARROW -> 50 * (1 shl level)   // 100, 200
        TowerType.BOMB  -> 80 * (1 shl level)
        TowerType.ICE   -> 60 * (1 shl level)
    }

    // 塔中心像素座標
    val centerX: Float get() {
        val (px, _) = gameMap.cellToPixel(col, row)
        return px + gameMap.cellSize / 2
    }
    val centerY: Float get() {
        val (_, py) = gameMap.cellToPixel(col, row)
        return py + gameMap.cellSize / 2
    }

    // 箭塔旋轉角度（對準目標）
    var aimAngle: Float = -45f   // 預設抵消emoji本身45度傾斜

    private val paint = Paint().apply { isAntiAlias = true }

    fun draw(canvas: Canvas) {
        val cs = gameMap.cellSize
        val (px, py) = gameMap.cellToPixel(col, row)

        // 塔底座
        paint.style = Paint.Style.FILL
        paint.color = when (type) {
            TowerType.ARROW -> Color.parseColor("#4a7c59")
            TowerType.BOMB  -> Color.parseColor("#7c4a4a")
            TowerType.ICE   -> Color.parseColor("#4a6a7c")
        }
        canvas.drawRoundRect(
            RectF(px + 4, py + 4, px + cs - 4, py + cs - 4),
            12f, 12f, paint
        )

        // 升級星星
        if (level > 1) {
            paint.textSize = cs * 0.22f
            for (i in 1 until level) {
                canvas.drawText("★", px + 4 + (i - 1) * cs * 0.22f, py + cs - 4, paint)
            }
        }

        // 塔 emoji
        paint.textSize = cs * 0.55f

        when (type) {
            TowerType.ARROW -> {
                // 箭塔需要旋轉對準目標
                // emoji本身朝右上45度，aimAngle=0時抵消，再加上實際朝向
                canvas.save()
                canvas.rotate(aimAngle + 45f, centerX, centerY)
                canvas.drawText("🏹", centerX - cs * 0.28f, centerY + cs * 0.2f, paint)
                canvas.restore()
            }
            TowerType.BOMB -> canvas.drawText("💣", centerX - cs * 0.28f, centerY + cs * 0.2f, paint)
            TowerType.ICE  -> canvas.drawText("❄️", centerX - cs * 0.28f, centerY + cs * 0.2f, paint)
        }
    }
}