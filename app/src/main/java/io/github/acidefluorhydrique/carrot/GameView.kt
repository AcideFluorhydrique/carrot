package io.github.acidefluorhydrique.carrot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val thread: GameThread
    private val paint = Paint()

    init {
        holder.addCallback(this)
        thread = GameThread(holder, this)
        isFocusable = true
    }

    // SurfaceHolder.Callback
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
            } catch (e: InterruptedException) {
                // 繼續等待
            }
        }
    }

    // 每幀更新遊戲邏輯（暫時為空）
    fun update() {
        // 後續在這裡更新敵人、子彈、塔等
    }

    // 每幀渲染
    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // 背景
        canvas.drawColor(Color.parseColor("#1a1a2e"))

        // 暫時顯示一個測試文字，確認畫面正常
        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.isAntiAlias = true
        canvas.drawText("遊戲初始化成功", 100f, 100f, paint)
    }
}