package com.example.myapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposeControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statusButtonHasAccessibleClickAction() {
        var clicks = 0
        composeRule.setContent {
            HoldableActionButton(text = "ПРИНЯТО", requireHold = true, onClick = { clicks++ })
        }

        composeRule.onNodeWithText("ПРИНЯТО")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, clicks)
    }

    @Test
    fun checkboxHasStateAndLargeTouchTarget() {
        composeRule.setContent {
            var checked by remember { mutableStateOf(false) }
            TelegramCheckbox(checked = checked, onClick = { checked = !checked })
        }
        val checkbox = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        )

        checkbox.assertIsOff()
        checkbox.performClick()
        checkbox.assertIsOn()
        val widthPx = checkbox.fetchSemanticsNode().size.width
        val minimumWidthPx = with(composeRule.density) { 48.dp.toPx() }
        assertTrue(widthPx >= minimumWidthPx)
    }
}
