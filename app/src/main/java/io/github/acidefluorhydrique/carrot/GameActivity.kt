// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager

class GameActivity : Activity() {

    private lateinit var gameView: GameView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        gameView.onActivityResume()
    }

    override fun onPause() {
        gameView.onActivityPause()
        super.onPause()
    }

    override fun onDestroy() {
        gameView.onActivityDestroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!gameView.onBackPressed()) {
            super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }
}
