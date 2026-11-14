package com.example.myapplication

import android.content.Context

object CardSettings {
    const val KEY_ARTICLE = "show_article"
    const val KEY_BRAND = "show_brand"
    const val KEY_COLOR = "show_color"
    const val KEY_UPPER = "show_upper"
    const val KEY_LINING = "show_lining"
    const val KEY_INSOLE = "show_insole"
    const val KEY_OUTSOLE = "show_outsole"
    const val KEY_PACKAGING = "show_packaging"
    const val KEY_FILE_COMMENTS = "show_file_comments"
    const val KEY_NOTE = "show_note"
    const val KEY_STATUS_DATE = "show_status_date"
    const val KEY_TEST_DATES = "show_test_dates"
    const val KEY_MATERIALS = "show_materials"
    const val KEY_COMMENTS = "show_comments"
    const val KEY_DATES = "show_dates"
    const val KEY_TESTS = "show_tests"

    private fun prefs(c: Context) = c.getSharedPreferences("QC_PREFS", Context.MODE_PRIVATE)

    fun isVisible(c: Context, key: String): Boolean = prefs(c).getBoolean(key, true)
    fun setVisible(c: Context, key: String, v: Boolean) { prefs(c).edit().putBoolean(key, v).apply() }

    fun resetAll(c: Context) {
        val e = prefs(c).edit()
        listOf(KEY_ARTICLE, KEY_BRAND, KEY_COLOR, KEY_UPPER, KEY_LINING, KEY_INSOLE, KEY_OUTSOLE,
            KEY_PACKAGING, KEY_FILE_COMMENTS, KEY_NOTE, KEY_STATUS_DATE, KEY_TEST_DATES)
            .forEach { e.putBoolean(it, true) }
        e.apply()
    }
}