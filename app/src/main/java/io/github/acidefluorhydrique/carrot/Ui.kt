package io.github.acidefluorhydrique.carrot

import kotlin.math.max
import kotlin.math.min

/**
 * 全域介面縮放。
 *
 * 舊版所有 UI 尺寸都是硬編碼的像素值，在高解析度手機上會小到看不清楚。
 * 這裡以「橫向時螢幕高度約等於 380dp」為基準推算縮放係數，
 * 不依賴系統回報的 density，因此在模擬器與實機上都穩定。
 */
object Ui {

    private const val BASE_HEIGHT = 380f

    var scale: Float = 1f
        private set
    var screenWidth: Int = 0
        private set
    var screenHeight: Int = 0
        private set

    fun onSurfaceChanged(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        scale = (height / BASE_HEIGHT).coerceIn(1f, 4.5f)
    }

    /** 以基準尺寸換算成實際像素。 */
    fun dp(value: Float): Float = value * scale

    /** 頂部資訊列高度。 */
    val topBarHeight: Float get() = dp(46f)

    /** 底部選塔列高度。 */
    val bottomBarHeight: Float get() = dp(62f)

    fun clamp(value: Float, lo: Float, hi: Float): Float = max(lo, min(hi, value))
}
