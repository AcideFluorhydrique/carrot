package io.github.acidefluorhydrique.carrot

import android.app.Activity
import android.os.Bundle

class GameActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 用 GameView 取代 XML layout
        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        gameView.holder.surface  // 讓 surfaceDestroyed 正常觸發
    }
}