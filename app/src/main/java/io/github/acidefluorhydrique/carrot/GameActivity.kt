package io.github.acidefluorhydrique.carrot

import android.app.Activity
import android.os.Bundle

class GameActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}