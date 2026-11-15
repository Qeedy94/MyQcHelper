package com.example.myapplication

import android.content.SharedPreferences

object ModelProgressStore {
    const val UPPER = "upper"
    const val LINING = "lining"
    const val INSOLE = "insole"
    const val OUTSOLE = "outsole"
    const val DECISION = "decision"
    const val NOTE = "note"
    const val DATE = "date"
    const val TEST_DATE = "testDate"
    const val TEST_RECEIVED_DATE = "testReceivedDate"

    private const val MIGRATION_VERSION_KEY = "model_progress_scope_version"
    private const val MIGRATION_VERSION = 1

    private val fields = listOf(
        UPPER,
        LINING,
        INSOLE,
        OUTSOLE,
        DECISION,
        NOTE,
        DATE,
        TEST_DATE,
        TEST_RECEIVED_DATE
    )

    fun key(orderId: String, article: String, field: String): String =
        "model|${orderId.length}|$orderId|${article.length}|$article|$field"

    internal fun legacyKey(article: String, field: String): String = "${article}_$field"

    internal fun createMigrationEntries(
        storedValues: Map<String, *>,
        orders: List<Order>
    ): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        orders.forEach { order ->
            order.models.forEach { model ->
                fields.forEach { field ->
                    val scopedKey = key(order.id, model.article, field)
                    val legacyKey = legacyKey(model.article, field)
                    if (!storedValues.containsKey(scopedKey)) {
                        val value = storedValues[legacyKey]
                        if (value is String || value is Boolean) {
                            result[scopedKey] = value
                        }
                    }
                }
            }
        }
        return result
    }

    fun migrateLegacyData(sharedPrefs: SharedPreferences, orders: List<Order>) {
        if (sharedPrefs.getInt(MIGRATION_VERSION_KEY, 0) >= MIGRATION_VERSION) return

        val editor = sharedPrefs.edit()
        createMigrationEntries(sharedPrefs.all, orders).forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        editor.putInt(MIGRATION_VERSION_KEY, MIGRATION_VERSION).apply()
    }
}
