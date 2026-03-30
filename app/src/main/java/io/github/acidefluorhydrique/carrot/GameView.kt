package io.github.acidefluorhydrique.carrot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val thread: GameThread
    private val gameMap = GameMap()          // ← 新增

    init {
        holder.addCallback(this)
        thread = GameThread(holder, this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread.running = true
        thread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

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

    fun update() {}

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawColor(Color.parseColor("#1a1a2e"))
        gameMap.draw(canvas)                 // ← 新增
    }
}