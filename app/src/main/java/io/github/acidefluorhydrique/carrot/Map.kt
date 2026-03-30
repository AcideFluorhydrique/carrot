package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class GameMap {

    companion object {
        const val COLS = 16
        const val ROWS = 8
        const val CELL_SIZE = 60f

        const val EMPTY = 0    // 可放塔的空地
        const val PATH = 1     // 敵人行走路徑
        const val BLOCKED = 2  // 不可放塔（預留）
    }

    // 地圖格子類型
    val grid = Array(ROWS) { IntArray(COLS) }

    // 路徑座標序列（敵人按此順序移動）
    val pathPoints: List<Pair<Int, Int>>  // (col, row)

    init {
        // 定義路徑
        val path = mutableListOf(
            0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0, 6 to 0,
            6 to 1,
            6 to 2, 7 to 2, 8 to 2, 9 to 2, 10 to 2, 11 to 2, 12 to 2, 13 to 2,
            13 to 3,
            13 to 4, 14 to 4, 15 to 4
        )
        pathPoints = path

        // 把路徑格子標記到 grid
        for ((col, row) in path) {
            grid[row][col] = PATH
        }
    }

    // 格子左上角的像素座標
    fun cellToPixel(col: Int, row: Int): Pair<Float, Float> {
        return Pair(col * CELL_SIZE, row * CELL_SIZE)
    }

    // 觸摸點轉格子座標
    fun pixelToCell(x: Float, y: Float): Pair<Int, Int> {
        return Pair((x / CELL_SIZE).toInt(), (y / CELL_SIZE).toInt())
    }

    fun isValidCell(col: Int, row: Int): Boolean {
        return col in 0 until COLS && row in 0 until ROWS
    }

    fun canPlaceTower(col: Int, row: Int): Boolean {
        return isValidCell(col, row) && grid[row][col] == EMPTY
    }

    // 渲染地圖
    private val paint = Paint()

    fun draw(canvas: Canvas) {
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val (x, y) = cellToPixel(col, row)
                val rect = RectF(x + 1, y + 1, x + CELL_SIZE - 1, y + CELL_SIZE - 1)

                paint.style = Paint.Style.FILL
                paint.color = when (grid[row][col]) {
                    PATH -> Color.parseColor("#8B6914")    // 泥土路徑，棕色
                    else -> Color.parseColor("#2d5a27")    // 草地，深綠
                }
                canvas.drawRect(rect, paint)

                // 格子邊框
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = Color.parseColor("#1a1a1a")
                canvas.drawRect(rect, paint)
            }
        }

        // 畫起點標記
        val (startX, startY) = cellToPixel(pathPoints.first().first, pathPoints.first().second)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FF4444")
        canvas.drawCircle(startX + CELL_SIZE / 2, startY + CELL_SIZE / 2, 15f, paint)

        // 畫終點標記（蘿蔔位置）
        val (endX, endY) = cellToPixel(pathPoints.last().first, pathPoints.last().second)
        paint.color = Color.parseColor("#FF8800")
        canvas.drawCircle(endX + CELL_SIZE / 2, endY + CELL_SIZE / 2, 15f, paint)
    }
}