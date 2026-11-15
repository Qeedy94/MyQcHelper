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
}
