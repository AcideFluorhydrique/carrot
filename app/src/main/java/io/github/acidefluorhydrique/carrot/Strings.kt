// SPDX-FileCopyrightText: 2026 AcideFluorhydrique
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.acidefluorhydrique.carrot

import android.content.Context
import android.content.res.Resources
import android.util.SparseArray
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

    private class Formatted(val args: Array<out Any>, val value: String)

    /**
     * 帶參數字串的最近結果，依資源 id 分組。HUD 每一幀都會格式化「第 7 波 · 剩 8」、
     * 塔價、分數，而 java.util.Formatter 很慢，參數卻多半和上一幀一樣。
     * 同一個 id 一幀內可能用不同參數呼叫好幾次（選塔列八座塔的價格），所以每組留幾筆。
     */
    private val formatted = SparseArray<ArrayList<Formatted>>()
    private const val FORMATTED_PER_ID = 12

    fun init(context: Context) {
        resources = context.resources
        cache.clear()
        synchronized(formatted) { formatted.clear() }
    }

    fun get(id: Int): String {
        cache[id]?.let { return it }
        val res = resources ?: return ""
        val value = runCatching { res.getString(id) }.getOrDefault("")
        if (value.isNotEmpty()) cache[id] = value
        return value
    }

    fun format(id: Int, vararg args: Any): String {
        synchronized(formatted) {
            val list = formatted.get(id)
            if (list != null) {
                for (i in list.indices) {
                    if (list[i].args.contentEquals(args)) return list[i].value
                }
            }
        }
        val res = resources ?: return ""
        val value = runCatching { res.getString(id, *args) }.getOrDefault("")
        if (value.isEmpty()) return value
        synchronized(formatted) {
            var list = formatted.get(id)
            if (list == null) {
                list = ArrayList(4)
                formatted.put(id, list)
            }
            if (list.size >= FORMATTED_PER_ID) list.removeAt(0)
            list.add(Formatted(args, value))
        }
        return value
    }
}
