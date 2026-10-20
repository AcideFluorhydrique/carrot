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
                // 硬體 Canvas：這條執行緒只錄製繪製指令，像素交給 GPU 算。
                // 以前的 lockCanvas() 是軟體繪圖，每一幀都在這條執行緒上把整個螢幕的像素重算一遍；
                // 在 2392×1080 的螢幕上實測，這條執行緒吃滿一顆核心，其中近九成花在 Skia 的軟體點陣化
                // （全螢幕漸層、半透明混色、反鋸齒邊緣），遊戲邏輯不到一成。
                // minSdk 26 正是這個 API 開始提供的版本。
                canvas = surfaceHolder.lockHardwareCanvas()
                if (canvas != null) {
                    synchronized(surfaceHolder) {
                        gameView.update()
                        gameView.render(canvas)
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
