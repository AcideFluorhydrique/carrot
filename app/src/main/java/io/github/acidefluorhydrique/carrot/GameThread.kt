// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.graphics.Canvas
import android.view.SurfaceHolder

class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView
) : Thread("carrot-game-loop") {

    @Volatile
    var running = false

    override fun run() {
        while (running) {
            val startTime = System.nanoTime()

            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    synchronized(surfaceHolder) {
                        gameView.update()
                        gameView.draw(canvas)
                    }
                }
            } catch (e: IllegalStateException) {
                // Surface 正在被回收，下一輪重試
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (e: IllegalStateException) {
                        // 忽略：surface 已失效
                    }
                }
            }

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000L
            val sleepMs = TARGET_FRAME_MS - elapsedMs
            if (sleepMs > 0) {
                try {
                    sleep(sleepMs)
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                    return
                }
            }
        }
    }

    companion object {
        private const val TARGET_FPS = 60
        private const val TARGET_FRAME_MS = 1000L / TARGET_FPS
    }
}
