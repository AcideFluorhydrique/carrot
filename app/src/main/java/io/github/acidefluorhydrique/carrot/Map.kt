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
    var pathPoints: List<Pair<Int, Int>> = emptyList()
        private set

    init {
        loadLevel(GameLevels.default)
    }

    fun loadLevel(level: LevelConfig) {
        for (row in 0 until ROWS) {
            grid[row].fill(EMPTY)
        }
        pathPoints = level.path
        for ((col, row) in pathPoints) {
            grid[row][col] = PATH
        }
    }

    fun initSize(screenWidth: Int, screenHeight: Int) {
        val HUD_TOP = 70f       // 頂部HUD高度
        val HUD_BOTTOM = 102f   // 底部選塔欄高度

        val availableH = screenHeight - HUD_TOP - HUD_BOTTOM

        cellSize = minOf(
            screenWidth.toFloat() / COLS,
            availableH / ROWS
        )
        // 水平居中，垂直從HUD下方開始
        offsetX = (screenWidth - cellSize * COLS) / 2f
        offsetY = HUD_TOP + (availableH - cellSize * ROWS) / 2f
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

    private val paint = Paint().apply { isAntiAlias = true }

    fun draw(canvas: Canvas) {
        drawMapFrame(canvas)

        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val (x, y) = cellToPixel(col, row)
                val rect = RectF(x + 2, y + 2, x + cellSize - 2, y + cellSize - 2)

                paint.style = Paint.Style.FILL
                paint.color = when (grid[row][col]) {
                    PATH -> Color.parseColor("#A77935")
                    BLOCKED -> Color.parseColor("#2E5134")
                    else -> if ((row + col) % 2 == 0) Color.parseColor("#3F793F") else Color.parseColor("#376F38")
                }
                canvas.drawRoundRect(rect, 7f, 7f, paint)

                if (grid[row][col] == PATH) {
                    paint.color = Color.parseColor("#28FFF1C3")
                    canvas.drawRoundRect(
                        RectF(rect.left + 4f, rect.top + 4f, rect.right - 4f, rect.top + cellSize * 0.28f),
                        5f, 5f, paint
                    )
                }

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f
                paint.color = if (grid[row][col] == PATH) Color.parseColor("#553E2813") else Color.parseColor("#331F361E")
                canvas.drawRoundRect(rect, 7f, 7f, paint)
            }
        }

        // 起點
        val (startX, startY) = cellToPixel(pathPoints.first().first, pathPoints.first().second)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#CCEF4444")
        canvas.drawCircle(startX + cellSize / 2, startY + cellSize / 2, cellSize * 0.32f, paint)
        paint.color = Color.WHITE
        paint.textSize = cellSize * 0.34f
        paint.isFakeBoldText = true
        drawCentered("GO", startX + cellSize / 2, startY + cellSize / 2 + cellSize * 0.12f, canvas)

        // 終點（蘿蔔）
        val (endX, endY) = cellToPixel(pathPoints.last().first, pathPoints.last().second)
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#44FFFFFF")
        canvas.drawCircle(endX + cellSize / 2, endY + cellSize / 2, cellSize * 0.43f, paint)
        paint.textSize = cellSize * 0.58f
        canvas.drawText("🥕", endX + cellSize * 0.2f, endY + cellSize * 0.72f, paint)
    }

    private fun drawMapFrame(canvas: Canvas) {
        val left = offsetX - 8f
        val top = offsetY - 8f
        val right = offsetX + cellSize * COLS + 8f
        val bottom = offsetY + cellSize * ROWS + 8f

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#44000000")
        canvas.drawRoundRect(RectF(left, top + 5f, right, bottom + 5f), 18f, 18f, paint)

        paint.color = Color.parseColor("#284221")
        canvas.drawRoundRect(RectF(left, top, right, bottom), 18f, 18f, paint)
    }

    private fun drawCentered(text: String, x: Float, y: Float, canvas: Canvas) {
        val tw = paint.measureText(text)
        canvas.drawText(text, x - tw / 2f, y, paint)
    }
}
