package io.github.acidefluorhydrique.carrot

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent

class GameActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView(this)
        setContentView(gameView)
    }

    // ← 關鍵：Activity層面攔截觸摸，直接轉發給GameView
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gameView.onTouchEvent(event)
    }

    override fun onPause() {
        super.onPause()
        gameView.holder.surface
    }
}