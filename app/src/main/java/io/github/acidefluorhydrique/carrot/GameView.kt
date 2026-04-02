package io.github.acidefluorhydrique.carrot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val thread: GameThread
    private val gameMap = GameMap()
    private val enemyManager = EnemyManager(gameMap)   // ← 新增
    private val hud = HudRenderer()                    // ← 新增
    private var screenWidth = 0                        // ← 新增
    private var screenHeight = 0                       // ← 新增

    init {
        holder.addCallback(this)
        thread = GameThread(holder, this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread.running = true
        thread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width                            // ← 新增
        screenHeight = height                          // ← 新增
        gameMap.initSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        thread.running = false
        while (retry) {
            try {
                thread.join()
                retry = false
            } catch (e: InterruptedException) {}
        }
    }

    fun update() {
        enemyManager.update()                          // ← 新增
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawColor(Color.parseColor("#1a1a2e"))
        gameMap.draw(canvas)
        enemyManager.draw(canvas)                      // ← 新增
        hud.draw(canvas, screenWidth, screenHeight)    // ← 新增
    }
}