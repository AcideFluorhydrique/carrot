// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

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

enum class ScreenMode { MAIN_MENU, CHAPTER_SELECT, LEVEL_MAP, SETTINGS, LANGUAGE, HELP, PLAYING }

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var thread: GameThread? = null

    private val saveRepository = SaveRepository(context.applicationContext)
    private val soundEngine = SoundEngine(context.applicationContext)

    private val gameMap = GameMap()
    private val enemyManager = EnemyManager(gameMap)
    private val towerManager = TowerManager(gameMap)
    private val obstacleManager = ObstacleManager(gameMap)
    private val hud = HudRenderer()
    private val menu = MenuRenderer()
    private val selectBar = TowerSelectBar()
    private val upgradePanel = TowerUpgradePanel()

    private var screenWidth = 0
    private var screenHeight = 0
    @Volatile
    private var screenMode = ScreenMode.MAIN_MENU

    private val backgroundPaint = Paint().apply { isAntiAlias = true }

    /** 背景漸層只在章節配色或畫面高度改變時重建，不必每幀配置一個新的 Shader。 */
    private var backgroundShader: Shader? = null
    private var backgroundShaderTheme: ChapterTheme? = null
    private var backgroundShaderHeight = 0

    private var autoSaveFrame = 0
    private var resultRecorded = false
    private var resultStars = 0
    private var bestStarsBefore = 0
    /** 無盡模式這一局開始前的最佳波次，結算畫面判斷是否破紀錄用。 */
    private var endlessBestBefore = 0
    private var settingsFromPause = false
    private var resetArmed = false
    private var statusBeforePause = GameStatus.PLAYING

    // 進度資料快取：draw() 每秒跑 60 次，不該每次都去翻 SharedPreferences
    private var cachedHasSave = false
    private var cachedStars: Map<Int, Int> = emptyMap()
    private var cachedCompleted: Set<Int> = emptySet()
    private var cachedTotalStars = 0
    /** 解鎖判斷用：任一難度通過的關卡都算。 */
    private var cachedUnlocked: Set<Int> = emptySet()
    private var cachedEndlessBest = 0
    private var languageTag = LocaleManager.SYSTEM
    private var selectedChapter: Chapter = Chapters.default

    /** 是否有一場進行中的對局；決定存檔是否有效，與目前在哪個畫面無關。 */
    private var gameActive = false

    init {
        // 必須早於任何繪製；欄位初始化階段只會存放資源 id，不會查字串
        Strings.init(LocaleManager.localized(context))

        holder.addCallback(this)
        isFocusable = true

        Audio.engine = soundEngine
        soundEngine.setSoundEnabled(saveRepository.soundEnabled())
        soundEngine.setMusicEnabled(saveRepository.musicEnabled())

        languageTag = LocaleManager.stored(context)

        GameState.difficulty = saveRepository.difficulty()
        val level = GameLevels.byId(saveRepository.lastLevelId())
        GameState.reset(level)
        refreshProgress()
    }

    /** 星等與通關數依目前難度顯示；解鎖則兩種難度合併計算。 */
    private fun refreshProgress() {
        cachedHasSave = saveRepository.hasActiveSave()
        cachedStars = saveRepository.allStars(GameState.difficulty)
        cachedCompleted = cachedStars.filterValues { it > 0 }.keys
        cachedTotalStars = cachedStars.values.sum()
        cachedUnlocked = Difficulty.values()
            .flatMap { saveRepository.completedLevels(it) }
            .toSet()
        cachedEndlessBest = saveRepository.endlessBest()
    }

    /** 章節頁與關卡地圖右下角的難度鍵。 */
    private fun cycleDifficulty() {
        GameState.difficulty = GameState.difficulty.next()
        saveRepository.setDifficulty(GameState.difficulty)
        refreshProgress()
        Audio.play(Sfx.BUILD)
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
        saveCurrentGame()
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
        // 系統若重建 Activity，新的 GameView 可能已經接手，別把它的引擎清掉
        if (Audio.engine === soundEngine) Audio.engine = null
        soundEngine.release()
    }

    /** 回傳 true 表示已處理返回鍵。 */
    fun onBackPressed(): Boolean = synchronized(holder) { handleBack() }

    private fun handleBack(): Boolean = when (screenMode) {
        ScreenMode.MAIN_MENU -> false
        ScreenMode.CHAPTER_SELECT, ScreenMode.HELP -> {
            screenMode = ScreenMode.MAIN_MENU
            true
        }
        ScreenMode.LEVEL_MAP -> {
            screenMode = ScreenMode.CHAPTER_SELECT
            true
        }
        ScreenMode.SETTINGS -> {
            leaveSettings()
            true
        }
        ScreenMode.LANGUAGE -> {
            screenMode = ScreenMode.SETTINGS
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
                obstacleManager.update()
                towerManager.update(enemyManager.enemies, obstacleManager)
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
                gameActive = false
                bestStarsBefore = saveRepository.starsFor(GameState.level.id, GameState.difficulty)
                resultStars = GameState.stars
                saveRepository.recordResult(GameState.level.id, resultStars, GameState.difficulty)
                saveRepository.clearActiveSave()
                refreshProgress()
                towerManager.clearBuildType()
                towerManager.clearSelection()
                Audio.play(Sfx.WIN)
                celebrate()
            }
            GameStatus.DEFEAT -> {
                resultRecorded = true
                gameActive = false
                bestStarsBefore = saveRepository.starsFor(GameState.level.id, GameState.difficulty)
                resultStars = 0
                if (GameState.isEndless) saveRepository.recordEndlessWave(GameState.wave)
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
            Fx.burst(x, y, 26, Colors.of("#FFD75E"), Ui.dp(2.6f), Ui.dp(2.6f), 46)
            Fx.burst(x, y, 18, Colors.of("#7BE88C"), Ui.dp(2.2f), Ui.dp(2.2f), 42)
            Fx.ring(x, y, Ui.dp(40f), Colors.of("#FFF3C4"), 26)
        }
    }

    // ---- 繪製 ----

    /**
     * 由遊戲迴圈呼叫，畫一整幀。不覆寫 View.draw：那是給 view 階層用的，
     * SurfaceView 的實作會在畫布上打洞，這裡畫的是自己的 surface，用不到。
     *
     * 硬體 Canvas 不保留上一幀，所以每個畫面都必須從整面背景畫起。
     */
    fun render(canvas: Canvas) {
        val w = screenWidth
        val h = screenHeight
        if (w == 0 || h == 0) {
            canvas.drawColor(Color.BLACK)
            return
        }

        when (screenMode) {
            ScreenMode.MAIN_MENU -> menu.drawMain(
                canvas, w, h,
                MenuRenderer.mainItems(cachedHasSave),
                GameState.level,
                GameState.difficulty,
                cachedTotalStars,
                cachedCompleted.size,
                Endless.isUnlocked(cachedUnlocked),
                cachedEndlessBest
            )
            ScreenMode.CHAPTER_SELECT -> menu.drawChapters(
                canvas, w, h, cachedStars, cachedUnlocked, GameState.difficulty
            )
            ScreenMode.LEVEL_MAP -> menu.drawLevelMap(
                canvas, w, h, selectedChapter, cachedStars, cachedCompleted,
                cachedUnlocked, GameState.difficulty
            )
            ScreenMode.SETTINGS -> menu.drawSettings(
                canvas, w, h, soundEngine.isSoundOn, soundEngine.isMusicOn, languageTag, resetArmed
            )
            ScreenMode.LANGUAGE -> menu.drawLanguage(canvas, w, h, languageTag)
            ScreenMode.HELP -> menu.drawHelp(canvas, w, h)
            ScreenMode.PLAYING -> drawGame(canvas, w, h)
        }
    }

    private fun drawGame(canvas: Canvas, w: Int, h: Int) {
        drawGameBackground(canvas, w, h)

        canvas.save()
        canvas.translate(Fx.offsetX, Fx.offsetY)
        gameMap.draw(canvas)
        obstacleManager.draw(canvas)
        towerManager.draw(canvas)
        enemyManager.draw(canvas)
        Fx.draw(canvas)
        canvas.restore()
        Fx.drawFlash(canvas, w, h)

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
            GameStatus.DEFEAT -> hud.drawResultOverlay(
                canvas, w, h, false, 0, bestStarsBefore, false, endlessBestBefore
            )
            else -> Unit
        }
    }

    private fun drawGameBackground(canvas: Canvas, w: Int, h: Int) {
        val theme = GameState.level.chapter.theme
        if (backgroundShader == null || backgroundShaderTheme !== theme || backgroundShaderHeight != h) {
            backgroundShader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(theme.skyTopColor, theme.skyMidColor, theme.skyBottomColor),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
            backgroundShaderTheme = theme
            backgroundShaderHeight = h
        }
        backgroundPaint.shader = backgroundShader
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), backgroundPaint)
        backgroundPaint.shader = null

        backgroundPaint.color = theme.hillColor
        backgroundPaint.alpha = 120
        canvas.drawCircle(w * 0.18f, h * 0.78f, w * 0.32f, backgroundPaint)
        canvas.drawCircle(w * 0.74f, h * 0.82f, w * 0.38f, backgroundPaint)
        backgroundPaint.alpha = 255
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
            ScreenMode.CHAPTER_SELECT -> handleChapterTap(x, y)
            ScreenMode.LEVEL_MAP -> handleLevelMapTap(x, y)
            ScreenMode.SETTINGS -> handleSettingsTap(x, y)
            ScreenMode.LANGUAGE -> handleLanguageTap(x, y)
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
            MenuAction.ENDLESS -> {
                if (Endless.isUnlocked(cachedUnlocked)) {
                    startLevel(Endless.level)
                } else {
                    Audio.play(Sfx.DENY)
                }
            }
            MenuAction.LEVELS -> {
                refreshProgress()
                selectedChapter = GameState.level.chapter
                screenMode = ScreenMode.CHAPTER_SELECT
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

    private fun handleChapterTap(x: Float, y: Float) {
        if (menu.tappedBack(x, y, screenWidth, screenHeight)) {
            screenMode = ScreenMode.MAIN_MENU
            return
        }
        if (menu.tappedDifficulty(x, y, screenWidth, screenHeight)) {
            cycleDifficulty()
            return
        }
        val chapter = menu.chapterTap(x, y, screenWidth, screenHeight, cachedUnlocked) ?: return
        selectedChapter = chapter
        screenMode = ScreenMode.LEVEL_MAP
    }

    private fun handleLevelMapTap(x: Float, y: Float) {
        if (menu.tappedBack(x, y, screenWidth, screenHeight)) {
            screenMode = ScreenMode.CHAPTER_SELECT
            return
        }
        if (menu.tappedDifficulty(x, y, screenWidth, screenHeight)) {
            cycleDifficulty()
            return
        }
        val level = menu.levelMapTap(x, y, screenWidth, screenHeight, selectedChapter, cachedUnlocked) ?: return
        GameState.level = level
        saveRepository.setLastLevelId(level.id)
        startLevel(level)
    }

    /**
     * 語言頁：一次點擊直接選定。
     *
     * 不重建 Activity。畫面全是 Canvas 自繪、沒有任何 layout 要 inflate，
     * 而所有文字都經過 Strings，它的 Resources 是 LocaleManager.localized()
     * 用 createConfigurationContext 產生的，本來就不依賴 attachBaseContext。
     * 換掉那一份 Resources，下一幀就是新語言。
     *
     * 重建的代價全部落在主執行緒上：存兩次檔（這裡一次、surfaceDestroyed 再一次）、
     * 釋放並重建音效引擎、join 掉繪製執行緒，然後整個 GameView 重來。
     * SharedPreferences 的 apply() 還會在 onPause 被 QueuedWork 強制等到落盤，
     * 在慢速儲存的裝置上足以拖出好幾秒的凍結，甚至觸發 ANR。
     */
    private fun handleLanguageTap(x: Float, y: Float) {
        if (menu.tappedBack(x, y, screenWidth, screenHeight)) {
            screenMode = ScreenMode.SETTINGS
            return
        }
        val tag = menu.languageTap(x, y, screenWidth, screenHeight) ?: return
        if (tag == languageTag) {
            screenMode = ScreenMode.SETTINGS
            return
        }
        languageTag = tag
        LocaleManager.store(context, tag)
        Strings.init(LocaleManager.localized(context))
        Audio.play(Sfx.BUILD)
        screenMode = ScreenMode.SETTINGS
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
            SettingsAction.OPEN_LANGUAGE -> {
                resetArmed = false
                screenMode = ScreenMode.LANGUAGE
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
        if (GameState.isEndless && HudRenderer.nukeRect(screenWidth, screenHeight).contains(x, y)) {
            fireNuke()
            return
        }
        if (upgradePanel.onTap(x, y, screenWidth, screenHeight, towerManager)) return
        if (selectBar.onTap(x, y, screenWidth, screenHeight, towerManager)) return

        // 點障礙物＝指定集火，優先於蓋塔判定
        val (col, row) = gameMap.pixelToCell(x, y)
        if (obstacleManager.onTap(col, row)) {
            towerManager.clearSelection()
            towerManager.cancelPlacement()
            return
        }
        towerManager.onMapDown(x, y)
    }

    /** 見 [Nuke]：消滅畫面上所有敵人，首領最少扣一半；不給金幣、不碰障礙物。 */
    private fun fireNuke() {
        val price = Nuke.price(GameState.nukeUses)
        val targets = enemyManager.enemies.filter { it.isAlive }
        if (GameState.nukeCooldown > 0 || GameState.gold < price || targets.isEmpty()) {
            Audio.play(Sfx.DENY)
            return
        }
        GameState.spendGold(price)
        GameState.nukeUses++
        GameState.nukeCooldown = Nuke.COOLDOWN_FRAMES
        towerManager.cancelPlacement()

        for (enemy in targets) enemy.nuke(Nuke.bossFraction(price, enemy.maxHp))

        val cx = gameMap.offsetX + gameMap.cellSize * GameMap.COLS / 2f
        val cy = gameMap.offsetY + gameMap.cellSize * GameMap.ROWS / 2f
        val span = gameMap.cellSize * GameMap.COLS
        Fx.flash(34)
        Fx.shake(Ui.dp(10f), 40)
        Fx.ring(cx, cy, span * 0.35f, Colors.of("#FFF3C4"), 30, Ui.dp(8f))
        Fx.ring(cx, cy, span * 0.6f, Colors.of("#FFB547"), 40, Ui.dp(6f))
        Fx.ring(cx, cy, span * 0.85f, Colors.of("#FF6B3D"), 50, Ui.dp(4f))
        Fx.explosion(cx, cy, gameMap.cellSize * 3f)
        Fx.burst(cx, cy, 60, Colors.of("#FFD27A"), Ui.dp(4f), Ui.dp(3.2f), 60, gravity = -0.03f)
        Audio.play(Sfx.EXPLODE)
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

    /**
     * 開始一關。[selected] 可以是原始關卡，也可以是已經套過難度的版本
     * （重開時傳進來的就是 GameState.level）—— [Difficulty.apply] 一律從原始資料推導，不會疊加。
     */
    private fun startLevel(selected: LevelConfig) {
        // 無盡模式不分難度，也不改「上次玩的關卡」：主選單的「開始」仍然指向劇情進度。
        val endless = Endless.isEndless(selected)
        val level = if (endless) Endless.level else GameState.difficulty.apply(selected)
        selectedChapter = level.chapter
        saveRepository.clearActiveSave()
        if (!endless) saveRepository.setLastLevelId(level.id)
        Fx.clear()
        GameState.reset(level)
        gameMap.loadLevel(level)
        gameMap.initSize(screenWidth, screenHeight)
        towerManager.reset()
        obstacleManager.reset(level)
        enemyManager.reset(level)
        screenMode = ScreenMode.PLAYING
        gameActive = true
        resultRecorded = false
        resultStars = 0
        bestStarsBefore = saveRepository.starsFor(level.id, GameState.difficulty)
        endlessBestBefore = saveRepository.endlessBest()
        autoSaveFrame = 0
        saveCurrentGame()
    }

    private fun continueSavedGame() {
        val save = saveRepository.loadGame() ?: return
        // 存檔是在哪個難度開的就用哪個難度接回去，並讓選單跟著切過去，
        // 結算時星等才會記到對的那一份。
        GameState.difficulty = Difficulty.fromName(save.difficulty)
        saveRepository.setDifficulty(GameState.difficulty)
        val base = GameLevels.byId(save.levelId)
        val level = if (Endless.isEndless(base)) base else GameState.difficulty.apply(base)
        selectedChapter = level.chapter
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
        GameState.nukeUses = save.nukeUses.coerceAtLeast(0)
        GameState.nukeCooldown = save.nukeCooldown.coerceIn(0, Nuke.COOLDOWN_FRAMES)

        gameMap.loadLevel(level)
        gameMap.initSize(screenWidth, screenHeight)
        obstacleManager.restore(level, save.obstacles)
        towerManager.restore(save.towers)
        enemyManager.restore(level, save.enemyManager)

        screenMode = ScreenMode.PLAYING
        gameActive = true
        resultRecorded = false
        resultStars = 0
        bestStarsBefore = saveRepository.starsFor(level.id, GameState.difficulty)
        endlessBestBefore = saveRepository.endlessBest()
        autoSaveFrame = 0
    }

    private fun goToMainMenu() {
        gameActive = false
        if (GameState.isEndless) GameState.level = GameLevels.byId(saveRepository.lastLevelId())
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
        if (!gameActive) return
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
                obstacles = obstacleManager.snapshot(),
                savedAt = System.currentTimeMillis(),
                difficulty = GameState.difficulty.name,
                nukeUses = GameState.nukeUses,
                nukeCooldown = GameState.nukeCooldown
            )
        )
        cachedHasSave = true
    }
}
