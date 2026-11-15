package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun touchProtectionDefaultsToStandard() {
        assertEquals(TouchProtectionMode.STANDARD, TouchProtectionMode.fromPreference(null))
        assertEquals(TouchProtectionMode.STANDARD, TouchProtectionMode.fromPreference("unknown"))
    }

    @Test
    fun repeatedClickIsBlockedOnlyWithinInterval() {
        assertTrue(shouldAcceptProtectedClick(TouchProtectionMode.STANDARD, 0L, 1000L))
        assertFalse(shouldAcceptProtectedClick(TouchProtectionMode.STANDARD, 1000L, 1399L))
        assertTrue(shouldAcceptProtectedClick(TouchProtectionMode.STANDARD, 1000L, 1400L))
        assertTrue(shouldAcceptProtectedClick(TouchProtectionMode.OFF, 1000L, 1001L))
    }

    @Test
    fun reworkReasonsAreAppendedWithoutReplacingExistingNote() {
        assertEquals("Брак\nНе тот цвет", appendReworkReasons("", listOf("Брак", "Не тот цвет")))
        assertEquals(
            "Существующая заметка\nБрак",
            appendReworkReasons("Существующая заметка", listOf("Брак"))
        )
        assertEquals("Без изменений", appendReworkReasons("Без изменений", emptyList()))
    }

    @Test
    fun modelProgressKeysAreIndependentBetweenOrders() {
        val first = ModelProgressStore.key("order-one.xlsx", "MODEL-1", ModelProgressStore.DECISION)
        val second = ModelProgressStore.key("order-two.xlsx", "MODEL-1", ModelProgressStore.DECISION)

        assertFalse(first == second)
        assertEquals(first, ModelProgressStore.key("order-one.xlsx", "MODEL-1", ModelProgressStore.DECISION))
    }

    @Test
    fun legacyProgressIsCopiedToEveryMatchingOrder() {
        val orders = listOf(testOrder("order-one.xlsx"), testOrder("order-two.xlsx"))
        val stored = mapOf(
            "MODEL-1_decision" to "ПРИНЯТО",
            "MODEL-1_upper" to true,
            "MODEL-1_note" to "Сохранённая заметка",
            "OTHER_decision" to "НЕ ГОТОВО"
        )

        val migrated = ModelProgressStore.createMigrationEntries(stored, orders)

        assertEquals(6, migrated.size)
        orders.forEach { order ->
            assertEquals("ПРИНЯТО", migrated[ModelProgressStore.key(order.id, "MODEL-1", ModelProgressStore.DECISION)])
            assertEquals(true, migrated[ModelProgressStore.key(order.id, "MODEL-1", ModelProgressStore.UPPER)])
            assertEquals("Сохранённая заметка", migrated[ModelProgressStore.key(order.id, "MODEL-1", ModelProgressStore.NOTE)])
        }
        assertFalse(migrated.keys.any { it.contains("OTHER") })
    }

    @Test
    fun migrationDoesNotOverwriteExistingScopedValues() {
        val firstOrder = testOrder("order-one.xlsx")
        val secondOrder = testOrder("order-two.xlsx")
        val existingKey = ModelProgressStore.key(firstOrder.id, "MODEL-1", ModelProgressStore.DECISION)
        val stored = mapOf(
            "MODEL-1_decision" to "ПРИНЯТО",
            existingKey to "НЕ ГОТОВО"
        )

        val migrated = ModelProgressStore.createMigrationEntries(stored, listOf(firstOrder, secondOrder))

        assertFalse(migrated.containsKey(existingKey))
        assertEquals(
            "ПРИНЯТО",
            migrated[ModelProgressStore.key(secondOrder.id, "MODEL-1", ModelProgressStore.DECISION)]
        )
    }

    private fun testOrder(id: String): Order {
        val model = ModelInfo(
            article = "MODEL-1",
            brand = "Brand",
            color = "Black",
            upper = "Leather",
            lining = "Textile",
            insole = "Textile",
            outsole = "Rubber",
            pairsPerBox = "6",
            sizeBoxes = "36-6",
            sizeBoxesCount = 6,
            mixedBoxes = "",
            mixedBoxesCount = "0",
            comments = ""
        )
        return Order(
            id = id,
            factory = "Factory",
            format = "MEGA",
            deliveryNum = "1",
            date = "01.01.2026",
            models = listOf(model)
        )
    }
}
