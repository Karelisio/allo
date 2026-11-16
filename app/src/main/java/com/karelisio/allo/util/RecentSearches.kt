package com.karelisio.allo.util

import android.content.Context

/** Recherches récentes du journal (5 dernières, stockées localement). */
object RecentSearches {
    private const val KEY = "items"
    private fun prefs(c: Context) = c.getSharedPreferences("recent_searches", Context.MODE_PRIVATE)

    fun get(c: Context): List<String> = prefs(c).getString(KEY, "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun add(c: Context, q: String) {
        val list = (listOf(q) + get(c).filter { !it.equals(q, true) && !q.startsWith(it, true) }).take(5)
        prefs(c).edit().putString(KEY, list.joinToString("\n")).apply()
    }

    fun remove(c: Context, q: String) = prefs(c).edit().putString(KEY, get(c).filter { it != q }.joinToString("\n")).apply()
}
