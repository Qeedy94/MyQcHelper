package com.example.myapplication

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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

    @Test
    fun mondayFirstCalendarOffsetMatchesWeekdays() {
        assertEquals(0, mondayFirstDayOffset(2026, 5))
        assertEquals(6, mondayFirstDayOffset(2026, 1))
    }

    @Test
    fun articleSearchRespectsSelectedFactories() {
        val first = testOrder("order-one.xlsx")
        val second = testOrder("order-two.xlsx").copy(factory = "Other factory")

        assertEquals(
            listOf(first to first.models.single()),
            searchModelsByArticle(listOf(first, second), "model", setOf("Factory"))
        )
        assertEquals(2, searchModelsByArticle(listOf(first, second), "MODEL", emptySet()).size)
        assertTrue(searchModelsByArticle(listOf(first), "missing", emptySet()).isEmpty())
    }

    @Test
    fun savedOrdersJsonRestoresLegacyFolderName() {
        val order = testOrder("order-one.xlsx").copy(internalFolderName = "")

        val decoded = decodeSavedOrders(com.google.gson.Gson().toJson(listOf(order)))

        assertEquals("Factory_MEGA_1_01_01_2026", decoded.single().internalFolderName)
        assertTrue(runCatching { decodeSavedOrders("{broken") }.isFailure)
    }

    @Test
    fun macroNamesDetermineModelsWithoutImages() {
        val order = testOrder("order-one.xlsx")

        assertEquals(1, getModelsWithoutImages(order, emptySet()).size)
        assertTrue(getModelsWithoutImages(order, setOf("model-1")).isEmpty())
        assertEquals(null, macroFileBaseName("extra_MODEL-1_123.jpg"))
        assertEquals("model-1", macroFileBaseName("MODEL 1_2.jpg"))
    }

    @Test
    fun numericArticleSuffixIsPreservedWhenItMatchesModel() {
        val articles = setOf("xid263_8")

        assertEquals("xid263_8", macroFileBaseNameForArticles("Xid263_8.jpg", articles))
        assertEquals("xid263_8-(1)", macroFileBaseNameForArticles("Xid263_8 (1).jpg", articles))
        assertEquals("model-1", macroFileBaseNameForArticles("MODEL 1_2.jpg", setOf("model-1")))
    }

    @Test
    fun galleryFilterKeepsOrdersWithPendingCounts() {
        val linked = testOrder("linked.xlsx")
        val pending = testOrder("pending.xlsx")
        val orders = listOf(linked, pending)
        val counts = mapOf(linked.id to 0)

        assertEquals(listOf(pending), galleryOrdersToDisplay(orders, false, counts))
        assertEquals(orders, galleryOrdersToDisplay(orders, true, counts))
        assertEquals(
            emptyList<Order>(),
            galleryOrdersToDisplay(orders, false, counts + (pending.id to 0))
        )
    }

    @Test
    fun updateSelectionDistinguishesNewAndCurrentVersions() {
        val xml = """
            <ListBucketResult>
              <Contents><Key>releases/Punta_destroyer_v1.7.9.zip</Key></Contents>
              <Contents><Key>releases/Punta_destroyer_v1.8.0.zip</Key></Contents>
            </ListBucketResult>
        """.trimIndent()

        val update = UpdateManager.findLatestUpdate(xml, "1.7.8")
        assertEquals("1.8.0", update?.version)
        assertTrue(update?.downloadUrl?.contains("releases/Punta_destroyer_v1.8.0.zip") == true)
        assertEquals(null, UpdateManager.findLatestUpdate(xml, "1.8.0"))
        assertEquals(null, UpdateManager.findLatestUpdate("not xml", "1.0"))
    }

    @Test
    fun excelParserReadsModelAndClosesInput() {
        val bytes = ByteArrayOutputStream().use { output ->
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("Order")
                sheet.createRow(0).createCell(19).setCellValue("Комментарии")
                val row = sheet.createRow(1)
                row.createCell(2).setCellValue("MODEL-1")
                row.createCell(5).setCellValue("Brand")
                row.createCell(6).setCellValue("Black")
                row.createCell(7).setCellValue("Leather")
                row.createCell(8).setCellValue("Textile")
                row.createCell(9).setCellValue("Textile")
                row.createCell(10).setCellValue("Rubber")
                row.createCell(11).setCellValue("6")
                row.createCell(12).setCellValue("36-6;37-2")
                row.createCell(13).setCellValue("")
                row.createCell(14).setCellValue("0")
                row.createCell(15).setCellValue("01.01.2026")
                row.createCell(19).setCellValue("Примечание")
                workbook.write(output)
            }
            output.toByteArray()
        }
        var closed = false
        val input = object : ByteArrayInputStream(bytes) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        val parsed = parseExcel(input, "Factory_1_OOO_джаззз.xlsx")

        assertEquals("Factory", parsed?.factory)
        assertEquals("MEGA", parsed?.format)
        assertEquals("1", parsed?.deliveryNum)
        assertEquals("01.01.2026", parsed?.date)
        assertEquals(8, parsed?.models?.single()?.sizeBoxesCount)
        assertEquals("Примечание", parsed?.models?.single()?.comments)
        assertTrue(closed)
        assertEquals(null, parseExcel(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "broken.xlsx"))
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
