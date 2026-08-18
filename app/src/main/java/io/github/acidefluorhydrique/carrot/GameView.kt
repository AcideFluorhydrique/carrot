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

enum class ScreenMode { MAIN_MENU, LEVEL_SELECT, SETTINGS, HELP, PLAYING }

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var thread: GameThread? = null

    private val saveRepository = SaveRepository(context.applicationContext)
    private val soundEngine = SoundEngine(context.applicationContext)

    private val gameMap = GameMap()
    private val enemyManager = EnemyManager(gameMap)
    private val towerManager = TowerManager(gameMap)
    private val hud = HudRenderer()
    private val menu = MenuRenderer()
    private val selectBar = TowerSelectBar()
    private val upgradePanel = TowerUpgradePanel()

    private var screenWidth = 0
    private var screenHeight = 0
    @Volatile
    private var screenMode = ScreenMode.MAIN_MENU

    private val backgroundPaint = Paint().apply { isAntiAlias = true }

    private var autoSaveFrame = 0
    private var resultRecorded = false
    private var resultStars = 0
    private var bestStarsBefore = 0
    private var settingsFromPause = false
    private var resetArmed = false
    private var statusBeforePause = GameStatus.PLAYING

    // 進度資料快取：draw() 每秒跑 60 次，不該每次都去翻 SharedPreferences
    private var cachedHasSave = false
    private var cachedStars: Map<Int, Int> = emptyMap()
    private var cachedCompleted: Set<Int> = emptySet()
    private var cachedTotalStars = 0

    init {
        // 必須早於任何繪製；欄位初始化階段只會存放資源 id，不會查字串
        Strings.init(context)

        holder.addCallback(this)
        isFocusable = true

        Audio.engine = soundEngine
        soundEngine.setSoundEnabled(saveRepository.soundEnabled())
        soundEngine.setMusicEnabled(saveRepository.musicEnabled())

        val level = GameLevels.byId(saveRepository.lastLevelId())
        GameState.reset(level)
        refreshProgress()
    }

    private fun refreshProgress() {
        cachedHasSave = saveRepository.hasActiveSave()
        cachedStars = saveRepository.allStars()
        cachedCompleted = cachedStars.filterValues { it > 0 }.keys
        cachedTotalStars = cachedStars.values.sum()
    }

    // ---- SurfaceHolder ----

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread = GameThread(holder, this).also {
            it.running = true
            it.start()
        }
        soundEngine.startMusic()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        Ui.onSurfaceChanged(width, height)
        gameMap.initSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val runningThread = thread
        thread = null
        runningThread?.running = false
        var retry = true
        while (retry) {
            try {
                runningThread?.join()
                retry = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                retry = false
            }
        }
        // 迴圈停下之後才存檔，才不會和遊戲執行緒同時碰同一批物件
        if (screenMode == ScreenMode.PLAYING) saveCurrentGame()
    }

    // ---- 生命週期轉接 ----

    fun onActivityPause() {
        synchronized(holder) { pauseGame() }
        soundEngine.pauseMusic()
    }

    fun onActivityResume() {
        soundEngine.startMusic()
    }

    fun onActivityDestroy() {
        Audio.engine = null
        soundEngine.release()
    }

    /** 回傳 true 表示已處理返回鍵。 */
    fun onBackPressed(): Boolean = synchronized(holder) { handleBack() }

    private fun handleBack(): Boolean = when (screenMode) {
        ScreenMode.MAIN_MENU -> false
        ScreenMode.LEVEL_SELECT, ScreenMode.HELP -> {
            screenMode = ScreenMode.MAIN_MENU
            true
        }
        ScreenMode.SETTINGS -> {
            leaveSettings()
            true
        }
        ScreenMode.PLAYING -> {
            when (GameState.status) {
                GameStatus.PLAYING -> pauseGame()
                GameStatus.PAUSED -> saveAndExitToMenu()
                else -> goToMainMenu()
            }
            true
        }
    }

    // ---- 更新 ----

    fun update() {
        menu.tick()
        if (screenMode != ScreenMode.PLAYING) return

        gameMap.tick()
        if (GameState.status == GameStatus.PLAYING) {
            val steps = GameState.speed.coerceIn(1, GameState.MAX_SPEED_STEP)
            for (i in 0 until steps) {
                GameState.tickTimers()
                enemyManager.update()
                towerManager.update(enemyManager.enemies)
                Fx.update()
                if (GameState.status != GameStatus.PLAYING) break
            }
            handleOutcome()
            autoSaveFrame++
            if (autoSaveFrame >= 180) {
                autoSaveFrame = 0
                saveCurrentGame()
            }
        } else if (GameState.status != GameStatus.PAUSED) {
            Fx.update()
        }
    }

    private fun handleOutcome() {
        if (resultRecorded) return
        when (GameState.status) {
            GameStatus.VICTORY -> {
                resultRecorded = true
                bestStarsBefore = saveRepository.starsFor(GameState.level.id)
                resultStars = GameState.stars
                saveRepository.recordResult(GameState.level.id, resultStars)
                saveRepository.clearActiveSave()
                refreshProgress()
                towerManager.clearBuildType()
                towerManager.clearSelection()
                Audio.play(Sfx.WIN)
                celebrate()
            }
            GameStatus.DEFEAT -> {
                resultRecorded = true
                bestStarsBefore = saveRepository.starsFor(GameState.level.id)
                resultStars = 0
                saveRepository.clearActiveSave()
                refreshProgress()
                towerManager.clearBuildType()
                towerManager.clearSelection()
                Audio.play(Sfx.LOSE)
                Fx.shake(Ui.dp(7f), 26)
            }
            else -> Unit
        }
    }

    private fun celebrate() {
        for (i in 0 until 5) {
            val x = screenWidth * (0.15f + 0.175f * i)
            val y = screenHeight * (0.3f + (i % 2) * 0.12f)
            Fx.burst(x, y, 26, Color.parseColor("#FFD75E"), Ui.dp(2.6f), Ui.dp(2.6f), 46)
            Fx.burst(x, y, 18, Color.parseColor("#7BE88C"), Ui.dp(2.2f), Ui.dp(2.2f), 42)
            Fx.ring(x, y, Ui.dp(40f), Color.parseColor("#FFF3C4"), 26)
        }
    }

    // ---- 繪製 ----

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val w = screenWidth
        val h = screenHeight
        if (w == 0 || h == 0) return

        when (screenMode) {
            ScreenMode.MAIN_MENU -> menu.drawMain(
                canvas, w, h,
                MenuRenderer.mainItems(cachedHasSave),
                GameState.level,
                cachedTotalStars,
                cachedCompleted.size
            )
            ScreenMode.LEVEL_SELECT -> menu.drawLevels(
                canvas, w, h,
                { id -> cachedStars[id] ?: 0 },
                cachedCompleted
            )
            ScreenMode.SETTINGS -> menu.drawSettings(
                canvas, w, h, soundEngine.isSoundOn, soundEngine.isMusicOn, resetArmed
            )
            ScreenMode.HELP -> menu.drawHelp(canvas, w, h)
            ScreenMode.PLAYING -> drawGame(canvas, w, h)
        }
    }

    private fun drawGame(canvas: Canvas, w: Int, h: Int) {
        drawGameBackground(canvas, w, h)

        canvas.save()
        canvas.translate(Fx.offsetX, Fx.offsetY)
        gameMap.draw(canvas)
        towerManager.draw(canvas)
        enemyManager.draw(canvas)
        Fx.draw(canvas)
        canvas.restore()

        hud.draw(canvas, w, h, enemyManager)

        if (GameState.status == GameStatus.PLAYING || GameState.status == GameStatus.PAUSED) {
            upgradePanel.draw(canvas, w, h, towerManager)
            selectBar.draw(canvas, w, h, towerManager.selectedType)
        }

        when (GameState.status) {
            GameStatus.PAUSED -> hud.drawPauseOverlay(canvas, w, h)
            GameStatus.VICTORY -> hud.drawResultOverlay(
                canvas, w, h, true, resultStars, bestStarsBefore,
                GameLevels.nextLevel(GameState.level.id) != null
            )
            GameStatus.DEFEAT -> hud.drawResultOverlay(canvas, w, h, false, 0, bestStarsBefore, false)
            else -> Unit
        }
    }

    private fun drawGameBackground(canvas: Canvas, w: Int, h: Int) {
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.parseColor("#122023"),
                Color.parseColor("#1A3025"),
                Color.parseColor("#223E22")
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), backgroundPaint)
        backgroundPaint.shader = null

        backgroundPaint.color = Color.parseColor("#1E4DA05A")
        canvas.drawCircle(w * 0.18f, h * 0.76f, w * 0.32f, backgroundPaint)
        canvas.drawCircle(w * 0.74f, h * 0.8f, w * 0.38f, backgroundPaint)
    }

    // ---- 觸控 ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        // 觸控在 UI 執行緒，遊戲迴圈在另一條執行緒；兩邊都會動到塔與敵人清單，
        // 因此共用 GameThread 所使用的同一把鎖。
        synchronized(holder) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> handleDown(x, y)
                MotionEvent.ACTION_MOVE -> if (screenMode == ScreenMode.PLAYING) towerManager.onMapMove(x, y)
                MotionEvent.ACTION_UP -> if (screenMode == ScreenMode.PLAYING) towerManager.onMapUp()
                MotionEvent.ACTION_CANCEL -> towerManager.cancelPlacement()
            }
        }
        return true
    }

    private fun handleDown(x: Float, y: Float) {
        when (screenMode) {
            ScreenMode.MAIN_MENU -> handleMainTap(x, y)
            ScreenMode.LEVEL_SELECT -> handleLevelTap(x, y)
            ScreenMode.SETTINGS -> handleSettingsTap(x, y)
            ScreenMode.HELP -> if (MenuRenderer.backButtonRect(screenWidth, screenHeight).contains(x, y)) {
                screenMode = ScreenMode.MAIN_MENU
            }
            ScreenMode.PLAYING -> handleGameTap(x, y)
        }
    }

    private fun handleMainTap(x: Float, y: Float) {
        val items = MenuRenderer.mainItems(cachedHasSave)
        when (menu.mainTap(x, y, screenWidth, screenHeight, items)) {
            MenuAction.CONTINUE -> continueSavedGame()
            MenuAction.START -> startLevel(GameState.level)
            MenuAction.LEVELS -> {
                refreshProgress()
                screenMode = ScreenMode.LEVEL_SELECT
            }
            MenuAction.HELP -> screenMode = ScreenMode.HELP
            MenuAction.SETTINGS -> {
                settingsFromPause = false
                resetArmed = false
                screenMode = ScreenMode.SETTINGS
            }
            else -> Unit
        }
    }

    private fun handleLevelTap(x: Float, y: Float) {
        if (menu.tappedBack(x, y, screenWidth, screenHeight)) {
            screenMode = ScreenMode.MAIN_MENU
            return
        }
        val level = menu.levelTap(x, y, screenWidth, screenHeight, cachedCompleted) ?: return
        GameState.level = level
        saveRepository.setLastLevelId(level.id)
        startLevel(level)
    }

    private fun handleSettingsTap(x: Float, y: Float) {
        when (menu.settingsTap(x, y, screenWidth, screenHeight)) {
            SettingsAction.TOGGLE_SOUND -> {
                val next = !soundEngine.isSoundOn
                soundEngine.setSoundEnabled(next)
                saveRepository.setSoundEnabled(next)
                if (next) Audio.play(Sfx.BUILD)
                resetArmed = false
            }
            SettingsAction.TOGGLE_MUSIC -> {
                val next = !soundEngine.isMusicOn
                soundEngine.setMusicEnabled(next)
                saveRepository.setMusicEnabled(next)
                resetArmed = false
            }
            SettingsAction.RESET_PROGRESS -> {
                if (resetArmed) {
                    saveRepository.resetProgress()
                    GameState.reset(GameLevels.default)
                    saveRepository.setLastLevelId(GameLevels.default.id)
                    refreshProgress()
                    resetArmed = false
                    Audio.play(Sfx.SELL)
                } else {
                    resetArmed = true
                    Audio.play(Sfx.DENY)
                }
            }
            SettingsAction.BACK -> leaveSettings()
            SettingsAction.NONE -> Unit
        }
    }

    private fun leaveSettings() {
        resetArmed = false
        screenMode = if (settingsFromPause) ScreenMode.PLAYING else ScreenMode.MAIN_MENU
        settingsFromPause = false
    }

    private fun handleGameTap(x: Float, y: Float) {
        when (GameState.status) {
            GameStatus.VICTORY, GameStatus.DEFEAT -> handleResultTap(x, y)
            GameStatus.PAUSED -> handlePauseTap(x, y)
            GameStatus.PLAYING -> handlePlayingTap(x, y)
        }
    }

    private fun handlePlayingTap(x: Float, y: Float) {
        if (HudRenderer.pauseButtonRect(screenWidth).contains(x, y)) {
            pauseGame()
            return
        }
        if (HudRenderer.speedButtonRect(screenWidth).contains(x, y)) {
            GameState.cycleSpeed()
            Audio.play(Sfx.BUILD)
            return
        }
        if (HudRenderer.callWaveRect(screenWidth, screenHeight).contains(x, y)) {
            if (enemyManager.canCallNextWave()) {
                enemyManager.callNextWave()
            } else {
                Audio.play(Sfx.DENY)
            }
            return
        }
        if (upgradePanel.onTap(x, y, screenWidth, screenHeight, towerManager)) return
        if (selectBar.onTap(x, y, screenWidth, screenHeight, towerManager)) return
        towerManager.onMapDown(x, y)
    }

    private fun handlePauseTap(x: Float, y: Float) {
        val w = screenWidth
        val h = screenHeight
        when {
            HudRenderer.rowButtonRect(w, h, 0, 4, 0.55f).contains(x, y) -> resumeGame()
            HudRenderer.rowButtonRect(w, h, 1, 4, 0.55f).contains(x, y) -> startLevel(GameState.level)
            HudRenderer.rowButtonRect(w, h, 2, 4, 0.55f).contains(x, y) -> {
                settingsFromPause = true
                resetArmed = false
                screenMode = ScreenMode.SETTINGS
            }
            HudRenderer.rowButtonRect(w, h, 3, 4, 0.55f).contains(x, y) -> saveAndExitToMenu()
            HudRenderer.pauseButtonRect(w).contains(x, y) -> resumeGame()
        }
    }

    private fun handleResultTap(x: Float, y: Float) {
        val victory = GameState.status == GameStatus.VICTORY
        val next = GameLevels.nextLevel(GameState.level.id)
        val hasNext = victory && next != null
        val count = if (hasNext) 3 else 2
        var index = 0

        if (hasNext && next != null) {
            if (HudRenderer.resultButtonRect(screenWidth, screenHeight, index, count).contains(x, y)) {
                GameState.level = next
                saveRepository.setLastLevelId(next.id)
                startLevel(next)
                return
            }
            index++
        }
        if (HudRenderer.resultButtonRect(screenWidth, screenHeight, index, count).contains(x, y)) {
            startLevel(GameState.level)
            return
        }
        index++
        if (HudRenderer.resultButtonRect(screenWidth, screenHeight, index, count).contains(x, y)) {
            goToMainMenu()
        }
    }

    // ---- 流程 ----

    private fun startLevel(level: LevelConfig) {
        saveRepository.clearActiveSave()
        saveRepository.setLastLevelId(level.id)
        Fx.clear()
        GameState.reset(level)
        gameMap.loadLevel(level)
        gameMap.initSize(screenWidth, screenHeight)
        towerManager.reset()
        enemyManager.reset(level)
        screenMode = ScreenMode.PLAYING
        resultRecorded = false
        resultStars = 0
        bestStarsBefore = saveRepository.starsFor(level.id)
        autoSaveFrame = 0
        saveCurrentGame()
    }

    private fun continueSavedGame() {
        val save = saveRepository.loadGame() ?: return
        val level = GameLevels.byId(save.levelId)
        Fx.clear()
        GameState.reset(level)
        GameState.carrotHp = save.carrotHp.coerceIn(0, save.maxCarrotHp)
        GameState.maxCarrotHp = save.maxCarrotHp
        GameState.gold = save.gold
        GameState.wave = save.wave
        GameState.kills = save.kills
        GameState.goldEarned = save.goldEarned
        GameState.leaks = save.leaks
        GameState.speed = save.speed.coerceIn(1, GameState.MAX_SPEED_STEP)
        GameState.status = GameStatus.PLAYING

        gameMap.loadLevel(level)
        gameMap.initSize(screenWidth, screenHeight)
        towerManager.restore(save.towers)
        enemyManager.restore(level, save.enemyManager)

        screenMode = ScreenMode.PLAYING
        resultRecorded = false
        resultStars = 0
        bestStarsBefore = saveRepository.starsFor(level.id)
        autoSaveFrame = 0
    }

    private fun goToMainMenu() {
        towerManager.clearBuildType()
        towerManager.clearSelection()
        Fx.clear()
        refreshProgress()
        screenMode = ScreenMode.MAIN_MENU
    }

    private fun saveAndExitToMenu() {
        GameState.status = GameStatus.PLAYING
        saveCurrentGame()
        goToMainMenu()
    }

    fun pauseGame() {
        if (screenMode != ScreenMode.PLAYING) return
        if (GameState.status != GameStatus.PLAYING) return
        statusBeforePause = GameState.status
        GameState.status = GameStatus.PAUSED
        towerManager.cancelPlacement()
        saveCurrentGame()
    }

    private fun resumeGame() {
        if (screenMode != ScreenMode.PLAYING) return
        if (GameState.status != GameStatus.PAUSED) return
        GameState.status = statusBeforePause
    }

    fun saveCurrentGame() {
        if (screenMode != ScreenMode.PLAYING) return
        if (GameState.status == GameStatus.VICTORY || GameState.status == GameStatus.DEFEAT) return
        saveRepository.saveGame(
            GameSave(
                version = SaveRepository.SAVE_VERSION,
                levelId = GameState.level.id,
                carrotHp = GameState.carrotHp,
                maxCarrotHp = GameState.maxCarrotHp,
                gold = GameState.gold,
                wave = GameState.wave,
                kills = GameState.kills,
                goldEarned = GameState.goldEarned,
                leaks = GameState.leaks,
                speed = GameState.speed,
                enemyManager = enemyManager.snapshot(),
                towers = towerManager.snapshot(),
                savedAt = System.currentTimeMillis()
            )
        )
        cachedHasSave = true
    }
}
