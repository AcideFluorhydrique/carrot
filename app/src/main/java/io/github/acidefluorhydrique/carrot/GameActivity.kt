package io.github.acidefluorhydrique.carrot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val thread: GameThread
    private val gameMap = GameMap()
    private val enemyManager = EnemyManager(gameMap)
    private val towerManager = TowerManager(gameMap)
    private val hud = HudRenderer()
    private var towerSelectBar: TowerSelectBar? = null
    private var screenWidth = 0
    private var screenHeight = 0

    init {
        holder.addCallback(this)
        thread = GameThread(holder, this)
        isFocusable = true
        TowerManagerHolder.manager = towerManager
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread.running = true
        thread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        gameMap.initSize(width, height)
        towerSelectBar = TowerSelectBar(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        thread.running = false
        while (retry) {
            try { thread.join(); retry = false }
            catch (e: InterruptedException) {}
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            // 先判斷是否點到底欄
            val hitBar = towerSelectBar?.onTap(x, y) ?: false
            if (!hitBar) {
                // 點擊地圖區域放塔或升級
                towerManager.onTap(x, y)
            }
        }
        return true
    }

    fun update() {
        enemyManager.update()
        towerManager.update(enemyManager.enemies)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawColor(Color.parseColor("#1a1a2e"))
        gameMap.draw(canvas)
        towerManager.draw(canvas)
        enemyManager.draw(canvas)
        hud.draw(canvas, screenWidth, screenHeight)
        towerSelectBar?.draw(canvas, towerManager.selectedType)
    }
}