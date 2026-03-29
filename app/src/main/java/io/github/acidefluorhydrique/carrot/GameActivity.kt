package com.carrot.fantasy

import android.app.Activity
import android.os.Bundle

class GameActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 後續會替換成 GameView
        setContentView(R.layout.activity_main)
    }
}