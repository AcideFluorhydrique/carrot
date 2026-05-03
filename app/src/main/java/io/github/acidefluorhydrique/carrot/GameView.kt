package io.github.acidefluorhydrique.carrot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var thread: GameThread? = null
    private val gameMap = GameMap()
    private val enemyManager = EnemyManager(gameMap)
    private val towerManager = TowerManager(gameMap)
    private val hud = HudRenderer()
    private val menu = MenuRenderer()
    private var towerSelectBar: TowerSelectBar? = null
    private var towerUpgradePanel: TowerUpgradePanel? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenMode = ScreenMode.MAIN_MENU
    private val backgroundPaint = Paint()

    init {
        holder.addCallback(this)
        isFocusable = true
        TowerManagerHolder.manager = towerManager
        GameState.reset(GameLevels.default)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread = GameThread(holder, this).also {
            it.running = true
            it.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        gameMap.initSize(width, height)
        towerSelectBar = TowerSelectBar(width, height)
        towerUpgradePanel = TowerUpgradePanel(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        thread?.running = false
        while (retry) {
            try {
                thread?.join()
                thread = null
                retry = false
            }
            catch (e: InterruptedException) {}
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            when (screenMode) {
                ScreenMode.MAIN_MENU -> handleMainTap(x, y)
                ScreenMode.LEVEL_SELECT -> handleLevelTap(x, y)
                ScreenMode.PLAYING -> handleGameTap(x, y)
            }
        }
        return true
    }

    fun update() {
        if (screenMode != ScreenMode.PLAYING) return
        enemyManager.update()
        towerManager.update(enemyManager.enemies)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        when (screenMode) {
            ScreenMode.MAIN_MENU -> menu.drawMain(canvas, screenWidth, screenHeight)
            ScreenMode.LEVEL_SELECT -> menu.drawLevels(canvas, screenWidth, screenHeight)
            ScreenMode.PLAYING -> drawGame(canvas)
        }
    }

    private fun drawGame(canvas: Canvas) {
        drawGameBackground(canvas)
        gameMap.draw(canvas)
        towerManager.draw(canvas)
        enemyManager.draw(canvas)
        hud.draw(canvas, screenWidth, screenHeight)
        towerUpgradePanel?.draw(canvas, towerManager)
        towerSelectBar?.draw(canvas, towerManager.selectedType)
    }

    private fun drawGameBackground(canvas: Canvas) {
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, screenHeight.toFloat(),
            intArrayOf(
                Color.parseColor("#142427"),
                Color.parseColor("#1C3428"),
                Color.parseColor("#244325")
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), backgroundPaint)
        backgroundPaint.shader = null

        backgroundPaint.color = Color.parseColor("#224DA05A")
        val hillY = screenHeight * 0.74f
        canvas.drawCircle(screenWidth * 0.18f, hillY, screenWidth * 0.34f, backgroundPaint)
        canvas.drawCircle(screenWidth * 0.72f, hillY + 20f, screenWidth * 0.42f, backgroundPaint)
    }

    private fun handleMainTap(x: Float, y: Float) {
        when (menu.mainTap(x, y)) {
            MenuAction.START -> startLevel(GameState.level)
            MenuAction.LEVELS -> screenMode = ScreenMode.LEVEL_SELECT
            MenuAction.NONE -> Unit
        }
    }

    private fun handleLevelTap(x: Float, y: Float) {
        if (menu.tappedBack(x, y)) {
            screenMode = ScreenMode.MAIN_MENU
            return
        }
        val selectedLevel = menu.levelTap(x, y) ?: return
        GameState.level = selectedLevel
        startLevel(selectedLevel)
    }

    private fun handleGameTap(x: Float, y: Float) {
        if (GameState.status != GameStatus.PLAYING) {
            screenMode = ScreenMode.MAIN_MENU
            return
        }
        if (towerUpgradePanel?.onTap(x, y, towerManager) == true) return
        val hitBar = towerSelectBar?.onTap(x, y) ?: false
        if (!hitBar) {
            towerManager.onTap(x, y)
        }
    }

    private fun startLevel(level: LevelConfig) {
        GameState.reset(level)
        gameMap.loadLevel(level)
        towerManager.reset()
        enemyManager.reset(level)
        screenMode = ScreenMode.PLAYING
    }
}

enum class ScreenMode { MAIN_MENU, LEVEL_SELECT, PLAYING }
