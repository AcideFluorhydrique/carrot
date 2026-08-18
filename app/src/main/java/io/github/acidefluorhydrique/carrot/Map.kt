// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sin

class GameMap {

    companion object {
        const val COLS = 16
        const val ROWS = 8

        const val EMPTY = 0
        const val PATH = 1
        /** 永久地形：定義地圖形狀，打不掉也蓋不了。 */
        const val PERMANENT = 2
        /** 可摧毀的障礙物佔用中。 */
        const val OBSTACLE = 3
        /** 已被塔佔用。 */
        const val TOWER = 4
    }

    var cellSize = 60f
        private set
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    val grid = Array(ROWS) { IntArray(COLS) }

    var pathPoints: List<Pair<Int, Int>> = emptyList()
        private set

    private var frame = 0
    private val paint = Paint().apply { isAntiAlias = true }
    private val arrowPath = Path()

    /** 目前關卡所屬章節的配色。 */
    private val theme: ChapterTheme get() = GameState.level.chapter.theme

    init {
        loadLevel(GameLevels.default)
    }

    fun loadLevel(level: LevelConfig) {
        for (row in 0 until ROWS) grid[row].fill(EMPTY)
        pathPoints = level.path
        for ((col, row) in pathPoints) {
            if (isValidCell(col, row)) grid[row][col] = PATH
        }
        for ((col, row) in level.permanent) {
            if (isValidCell(col, row) && grid[row][col] == EMPTY) grid[row][col] = PERMANENT
        }
        for (spec in level.obstacles) {
            if (isValidCell(spec.col, spec.row) && grid[spec.row][spec.col] == EMPTY) {
                grid[spec.row][spec.col] = OBSTACLE
            }
        }
    }

    fun initSize(screenWidth: Int, screenHeight: Int) {
        val availableH = screenHeight - Ui.topBarHeight - Ui.bottomBarHeight
        cellSize = minOf(
            (screenWidth - Ui.dp(12f)) / COLS,
            availableH / ROWS
        ).coerceAtLeast(8f)
        offsetX = (screenWidth - cellSize * COLS) / 2f
        offsetY = Ui.topBarHeight + (availableH - cellSize * ROWS) / 2f
    }

    fun cellToPixel(col: Int, row: Int): Pair<Float, Float> =
        Pair(offsetX + col * cellSize, offsetY + row * cellSize)

    fun centerOf(col: Int, row: Int): Pair<Float, Float> =
        Pair(offsetX + col * cellSize + cellSize / 2f, offsetY + row * cellSize + cellSize / 2f)

    fun pixelToCell(x: Float, y: Float): Pair<Int, Int> = Pair(
        floor((x - offsetX) / cellSize).toInt(),
        floor((y - offsetY) / cellSize).toInt()
    )

    fun isValidCell(col: Int, row: Int): Boolean =
        col in 0 until COLS && row in 0 until ROWS

    fun canPlaceTower(col: Int, row: Int): Boolean =
        isValidCell(col, row) && grid[row][col] == EMPTY

    fun occupy(col: Int, row: Int) {
        if (isValidCell(col, row)) grid[row][col] = TOWER
    }

    fun release(col: Int, row: Int) {
        if (isValidCell(col, row) && grid[row][col] == TOWER) grid[row][col] = EMPTY
    }

    /** 障礙物被打掉之後，那一格就能蓋塔了。 */
    fun clearObstacle(col: Int, row: Int) {
        if (isValidCell(col, row) && grid[row][col] == OBSTACLE) grid[row][col] = EMPTY
    }

    fun isObstacle(col: Int, row: Int): Boolean =
        isValidCell(col, row) && grid[row][col] == OBSTACLE

    fun tick() {
        frame++
    }

    // ---- 繪製 ----

    fun draw(canvas: Canvas) {
        drawFrame(canvas)
        drawCells(canvas)
        drawPathArrows(canvas)
        drawStart(canvas)
        drawCarrot(canvas)
    }

    private fun drawFrame(canvas: Canvas) {
        val pad = Ui.dp(5f)
        val left = offsetX - pad
        val top = offsetY - pad
        val right = offsetX + cellSize * COLS + pad
        val bottom = offsetY + cellSize * ROWS + pad
        val radius = Ui.dp(12f)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#55000000")
        canvas.drawRoundRect(RectF(left, top + Ui.dp(4f), right, bottom + Ui.dp(4f)), radius, radius, paint)
        paint.color = theme.frameColor
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
    }

    private fun drawCells(canvas: Canvas) {
        val inset = cellSize * 0.03f
        val radius = cellSize * 0.12f
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val (x, y) = cellToPixel(col, row)
                val rect = RectF(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset)
                val cell = grid[row][col]

                paint.style = Paint.Style.FILL
                paint.color = when (cell) {
                    PATH -> theme.pathColorInt
                    PERMANENT -> theme.frameColor
                    else -> if ((row + col) % 2 == 0) theme.grassAColor else theme.grassBColor
                }
                canvas.drawRoundRect(rect, radius, radius, paint)

                if (cell == PATH) {
                    paint.color = theme.pathHighlightColor
                    canvas.drawRoundRect(
                        RectF(rect.left + inset * 2, rect.top + inset * 2, rect.right - inset * 2, rect.top + cellSize * 0.26f),
                        radius * 0.7f, radius * 0.7f, paint
                    )
                }

                if (cell == PERMANENT) {
                    paint.textSize = cellSize * 0.5f
                    val decorations = theme.decor
                    val decor = decorations[(row * 7 + col * 3) % decorations.size]
                    val tw = paint.measureText(decor)
                    canvas.drawText(decor, rect.centerX() - tw / 2f, rect.centerY() + cellSize * 0.18f, paint)
                }

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = Ui.dp(0.7f)
                paint.color = when (cell) {
                    PATH, PERMANENT -> theme.pathEdgeColor
                    else -> theme.grassLineColor
                }
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }
    }

    /** 在路徑上畫流動箭頭，讓玩家一眼看懂敵人走向。 */
    private fun drawPathArrows(canvas: Canvas) {
        if (pathPoints.size < 2) return
        paint.style = Paint.Style.FILL
        val step = 3
        var i = (frame / 14) % step
        while (i < pathPoints.size - 1) {
            val (c1, r1) = pathPoints[i]
            val (c2, r2) = pathPoints[i + 1]
            val (x1, y1) = centerOf(c1, r1)
            val (x2, y2) = centerOf(c2, r2)
            val mx = (x1 + x2) / 2f
            val my = (y1 + y2) / 2f
            val angle = atan2(y2 - y1, x2 - x1)

            canvas.save()
            canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat(), mx, my)
            val size = cellSize * 0.16f
            arrowPath.reset()
            arrowPath.moveTo(mx + size, my)
            arrowPath.lineTo(mx - size * 0.7f, my - size * 0.8f)
            arrowPath.lineTo(mx - size * 0.7f, my + size * 0.8f)
            arrowPath.close()
            paint.color = Color.parseColor("#4DFFF3D0")
            canvas.drawPath(arrowPath, paint)
            canvas.restore()
            i += step
        }
    }

    private fun drawStart(canvas: Canvas) {
        if (pathPoints.isEmpty()) return
        val (col, row) = pathPoints.first()
        val (cx, cy) = centerOf(col, row)
        val pulse = 1f + 0.08f * sin(frame * 0.09f)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#33EF4444")
        canvas.drawCircle(cx, cy, cellSize * 0.42f * pulse, paint)
        paint.color = Color.parseColor("#CCEF4444")
        canvas.drawCircle(cx, cy, cellSize * 0.3f, paint)

        paint.color = Color.WHITE
        paint.textSize = cellSize * 0.3f
        paint.isFakeBoldText = true
        val label = Strings.get(R.string.map_start)
        canvas.drawText(label, cx - paint.measureText(label) / 2f, cy + cellSize * 0.1f, paint)
        paint.isFakeBoldText = false
    }

    private fun drawCarrot(canvas: Canvas) {
        if (pathPoints.isEmpty()) return
        val (col, row) = pathPoints.last()
        val (cx0, cy) = centerOf(col, row)
        val hurt = GameState.carrotHurtTimer > 0
        val cx = if (hurt) cx0 + sin(frame * 1.6f) * cellSize * 0.08f else cx0

        val ratio = if (GameState.maxCarrotHp > 0) {
            GameState.carrotHp.toFloat() / GameState.maxCarrotHp
        } else 0f

        paint.style = Paint.Style.FILL
        paint.color = if (hurt) Color.parseColor("#66FF6B6B") else Color.parseColor("#33FFFFFF")
        canvas.drawCircle(cx, cy, cellSize * 0.46f, paint)

        // 血量圓環
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = Ui.dp(3f)
        paint.color = Color.parseColor("#55000000")
        canvas.drawCircle(cx, cy, cellSize * 0.42f, paint)
        paint.color = when {
            ratio > 0.6f -> Color.parseColor("#66E06B")
            ratio > 0.3f -> Color.parseColor("#F2C14E")
            else -> Color.parseColor("#EF4444")
        }
        val arcRect = RectF(cx - cellSize * 0.42f, cy - cellSize * 0.42f, cx + cellSize * 0.42f, cy + cellSize * 0.42f)
        canvas.drawArc(arcRect, -90f, 360f * ratio, false, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = cellSize * 0.55f
        val carrot = "🥕"
        canvas.drawText(carrot, cx - paint.measureText(carrot) / 2f, cy + cellSize * 0.2f, paint)
    }
}
