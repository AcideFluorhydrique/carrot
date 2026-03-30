package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class GameMap {

    companion object {
        const val COLS = 16
        const val ROWS = 8
        const val EMPTY = 0
        const val PATH = 1
        const val BLOCKED = 2
    }

    // ← 不再是 const，由 init(width, height) 計算
    var cellSize = 60f
        private set

    // 地圖左上角偏移（讓地圖居中）
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    val grid = Array(ROWS) { IntArray(COLS) }
    val pathPoints: List<Pair<Int, Int>>

    init {
        val path = mutableListOf(
            0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0, 6 to 0,
            6 to 1,
            6 to 2, 7 to 2, 8 to 2, 9 to 2, 10 to 2, 11 to 2, 12 to 2, 13 to 2,
            13 to 3,
            13 to 4, 14 to 4, 15 to 4
        )
        pathPoints = path
        for ((col, row) in path) {
            grid[row][col] = PATH
        }
    }

    // ← 新增：GameView 拿到屏幕尺寸後調用一次
    fun initSize(screenWidth: Int, screenHeight: Int) {
        cellSize = minOf(
            screenWidth.toFloat() / COLS,
            screenHeight.toFloat() / ROWS
        )
        // 計算居中偏移
        offsetX = (screenWidth - cellSize * COLS) / 2f
        offsetY = (screenHeight - cellSize * ROWS) / 2f
    }

    fun cellToPixel(col: Int, row: Int): Pair<Float, Float> {
        return Pair(offsetX + col * cellSize, offsetY + row * cellSize)
    }

    fun pixelToCell(x: Float, y: Float): Pair<Int, Int> {
        return Pair(
            ((x - offsetX) / cellSize).toInt(),
            ((y - offsetY) / cellSize).toInt()
        )
    }

    fun isValidCell(col: Int, row: Int): Boolean {
        return col in 0 until COLS && row in 0 until ROWS
    }

    fun canPlaceTower(col: Int, row: Int): Boolean {
        return isValidCell(col, row) && grid[row][col] == EMPTY
    }

    private val paint = Paint()

    fun draw(canvas: Canvas) {
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val (x, y) = cellToPixel(col, row)
                val rect = RectF(x + 1, y + 1, x + cellSize - 1, y + cellSize - 1)

                paint.style = Paint.Style.FILL
                paint.color = when (grid[row][col]) {
                    PATH -> Color.parseColor("#8B6914")
                    else -> Color.parseColor("#2d5a27")
                }
                canvas.drawRect(rect, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = Color.parseColor("#1a1a1a")
                canvas.drawRect(rect, paint)
            }
        }

        // 起點
        val (startX, startY) = cellToPixel(pathPoints.first().first, pathPoints.first().second)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FF4444")
        canvas.drawCircle(startX + cellSize / 2, startY + cellSize / 2, cellSize * 0.25f, paint)

        // 終點（蘿蔔）
        val (endX, endY) = cellToPixel(pathPoints.last().first, pathPoints.last().second)
        paint.color = Color.parseColor("#FF8800")
        canvas.drawCircle(endX + cellSize / 2, endY + cellSize / 2, cellSize * 0.25f, paint)
    }
}