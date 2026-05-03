package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.view.SurfaceHolder

class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView
) : Thread() {

    var running = false
    private val targetFps = 60
    private val targetTimeMs = 1000L / targetFps

    override fun run() {
        while (running) {
            val startTime = System.currentTimeMillis()

            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                val lockedCanvas = canvas
                if (lockedCanvas != null) {
                    synchronized(surfaceHolder) {
                        gameView.update()
                        gameView.draw(lockedCanvas)
                    }
                }
            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                }
            }

            // 控制幀率
            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = targetTimeMs - elapsed
            if (sleepTime > 0) sleep(sleepTime)
        }
    }
}
