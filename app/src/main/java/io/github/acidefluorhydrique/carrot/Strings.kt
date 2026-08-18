package io.github.acidefluorhydrique.carrot

import android.content.Context
import android.content.res.Resources
import java.util.concurrent.ConcurrentHashMap

/**
 * 畫面全部畫在 Canvas 上，繪製端拿不到 Context，
 * 因此用單例持有 Resources，讓 renderer 直接查字串。
 *
 * 語系切換時 Activity 會重建 → GameView 重建 → 這裡重新 init 並清快取。
 */
object Strings {

    @Volatile
    private var resources: Resources? = null

    /** 無參數字串每影格都會被查，快取起來省掉重複配置。 */
    private val cache = ConcurrentHashMap<Int, String>()

    fun init(context: Context) {
        resources = context.resources
        cache.clear()
    }

    fun get(id: Int): String {
        cache[id]?.let { return it }
        val res = resources ?: return ""
        val value = runCatching { res.getString(id) }.getOrDefault("")
        if (value.isNotEmpty()) cache[id] = value
        return value
    }

    fun format(id: Int, vararg args: Any): String {
        val res = resources ?: return ""
        return runCatching { res.getString(id, *args) }.getOrDefault("")
    }
}
