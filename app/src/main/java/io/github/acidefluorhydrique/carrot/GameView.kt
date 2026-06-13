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
    private val saveRepository = SaveRepository(context.applicationContext)
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
    private var lastSavedStatus: GameStatus = GameStatus.PLAYING
    private var autoSaveFrame = 0
    private var victoryRecorded = false

    init {
        holder.addCallback(this)
        isFocusable = true
        TowerManagerHolder.manager = towerManager
        GameState.reset(GameLevels.default)
        saveRepository.loadGame()?.let { save ->
            GameLevels.all.firstOrNull { it.id == save.levelId }?.let { GameState.level = it }
        }
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
        if (screenMode == ScreenMode.PLAYING) {
            saveCurrentGame()
        }
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
        val previousStatus = GameState.status
        enemyManager.update()
        towerManager.update(enemyManager.enemies)
        if (previousStatus == GameStatus.PLAYING && GameState.status == GameStatus.VICTORY && !victoryRecorded) {
            victoryRecorded = true
            saveRepository.markLevelCompleted(GameState.level.id)
            saveRepository.clearActiveSave()
        }
        if (previousStatus == GameStatus.PLAYING && GameState.status == GameStatus.DEFEAT) {
            saveRepository.clearActiveSave()
        }
        if (GameState.status == GameStatus.PLAYING) {
            autoSaveFrame++
            if (autoSaveFrame >= 180) {
                autoSaveFrame = 0
                saveCurrentGame()
            }
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        when (screenMode) {
            ScreenMode.MAIN_MENU -> menu.drawMain(
                canvas,
                screenWidth,
                screenHeight,
                saveRepository.hasActiveSave(),
                saveRepository.completedLevels()
            )
            ScreenMode.LEVEL_SELECT -> menu.drawLevels(
                canvas,
                screenWidth,
                screenHeight,
                saveRepository.completedLevels()
            )
            ScreenMode.PLAYING -> drawGame(canvas)
        }
    }

    private fun drawGame(canvas: Canvas) {
        drawGameBackground(canvas)
        gameMap.draw(canvas)
        towerManager.draw(canvas)
        enemyManager.draw(canvas)
        hud.draw(canvas, screenWidth, screenHeight)
        if (GameState.status == GameStatus.PLAYING) {
            towerUpgradePanel?.draw(canvas, towerManager)
            towerSelectBar?.draw(canvas, towerManager.selectedType)
        }
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
            MenuAction.CONTINUE -> continueSavedGame()
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
        if (GameState.status == GameStatus.VICTORY || GameState.status == GameStatus.DEFEAT) {
            screenMode = ScreenMode.MAIN_MENU
            return
        }
        if (HudRenderer.pauseButtonRect(screenWidth).contains(x, y)) {
            togglePause()
            return
        }
        if (GameState.status == GameStatus.PAUSED) {
            handlePauseTap(x, y)
            return
        }
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
        saveRepository.clearActiveSave()
        GameState.reset(level)
        gameMap.loadLevel(level)
        towerManager.reset()
        enemyManager.reset(level)
        screenMode = ScreenMode.PLAYING
        victoryRecorded = false
        autoSaveFrame = 0
        saveCurrentGame()
    }

    private fun continueSavedGame() {
        val save = saveRepository.loadGame() ?: return
        val level = GameLevels.all.firstOrNull { it.id == save.levelId } ?: GameLevels.default
        GameState.level = level
        GameState.carrotHp = save.carrotHp
        GameState.gold = save.gold
        GameState.wave = save.wave
        GameState.status = GameStatus.PLAYING
        gameMap.loadLevel(level)
        enemyManager.restore(level, save.enemyManager)
        towerManager.restore(save.towers)
        screenMode = ScreenMode.PLAYING
        victoryRecorded = false
        autoSaveFrame = 0
    }

    private fun togglePause() {
        when (GameState.status) {
            GameStatus.PLAYING -> pauseGame()
            GameStatus.PAUSED -> resumeGame()
            else -> Unit
        }
    }

    private fun handlePauseTap(x: Float, y: Float) {
        when {
            HudRenderer.pauseResumeRect(screenWidth, screenHeight).contains(x, y) -> resumeGame()
            HudRenderer.pauseSaveExitRect(screenWidth, screenHeight).contains(x, y) -> {
                saveCurrentGame()
                screenMode = ScreenMode.MAIN_MENU
                GameState.status = GameStatus.PLAYING
            }
            HudRenderer.pauseRestartRect(screenWidth, screenHeight).contains(x, y) -> startLevel(GameState.level)
        }
    }

    fun pauseGame() {
        if (screenMode == ScreenMode.PLAYING && GameState.status == GameStatus.PLAYING) {
            lastSavedStatus = GameState.status
            GameState.status = GameStatus.PAUSED
            saveCurrentGame()
        }
    }

    fun resumeGame() {
        if (screenMode == ScreenMode.PLAYING && GameState.status == GameStatus.PAUSED) {
            GameState.status = lastSavedStatus
        }
    }

    fun saveCurrentGame() {
        if (screenMode != ScreenMode.PLAYING) return
        if (GameState.status == GameStatus.VICTORY || GameState.status == GameStatus.DEFEAT) return
        saveRepository.saveGame(
            GameSave(
                version = 1,
                levelId = GameState.level.id,
                carrotHp = GameState.carrotHp,
                gold = GameState.gold,
                wave = GameState.wave,
                enemyManager = enemyManager.snapshot(),
                towers = towerManager.snapshot(),
                savedAt = System.currentTimeMillis()
            )
        )
    }
}

enum class ScreenMode { MAIN_MENU, LEVEL_SELECT, PLAYING }
