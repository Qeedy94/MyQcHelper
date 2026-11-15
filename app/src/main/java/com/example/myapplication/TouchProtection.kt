package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

const val TOUCH_PROTECTION_MODE_KEY = "touch_protection_mode"
const val STATUS_VIBRATION_ENABLED_KEY = "status_vibration_enabled"

enum class TouchProtectionMode(val preferenceValue: String, val label: String) {
    OFF("off", "Выключена"),
    STANDARD("standard", "Стандартная"),
    STRONG("strong", "Усиленная");

    companion object {
        fun fromPreference(value: String?): TouchProtectionMode =
            entries.firstOrNull { it.preferenceValue == value } ?: STANDARD
    }
}

fun getTouchProtectionMode(sharedPrefs: SharedPreferences): TouchProtectionMode =
    TouchProtectionMode.fromPreference(
        sharedPrefs.getString(TOUCH_PROTECTION_MODE_KEY, TouchProtectionMode.STANDARD.preferenceValue)
    )

internal fun shouldAcceptProtectedClick(
    mode: TouchProtectionMode,
    lastAcceptedAt: Long,
    now: Long,
    intervalMillis: Long = 400L
): Boolean = mode == TouchProtectionMode.OFF || lastAcceptedAt == 0L || now - lastAcceptedAt >= intervalMillis

internal fun appendReworkReasons(note: String, reasons: Collection<String>): String {
    if (reasons.isEmpty()) return note
    val additions = reasons.joinToString("\n")
    return if (note.isEmpty()) additions else "$note\n$additions"
}

object StatusHapticHelper {
    fun onStatusApplied(sharedPrefs: SharedPreferences, context: Context) {
        if (sharedPrefs.getBoolean(STATUS_VIBRATION_ENABLED_KEY, true)) {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(80L, 255))
            }
        }
    }
}

@Composable
fun HoldableActionArea(
    requireHold: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDurationMillis: Long = 600L,
    content: @Composable (holdProgress: Float) -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    var isHolding by remember { mutableStateOf(false) }
    var holdStartedAt by remember { mutableLongStateOf(0L) }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isHolding, holdStartedAt) {
        if (isHolding) {
            while (isHolding) {
                val elapsed = SystemClock.uptimeMillis() - holdStartedAt
                holdProgress = (elapsed.toFloat() / holdDurationMillis).coerceIn(0f, 1f)
                if (holdProgress >= 1f) break
                delay(16L)
            }
        } else {
            holdProgress = 0f
        }
    }

    val interactionModifier = when {
        !enabled -> Modifier
        requireHold -> Modifier.pointerInput(holdDurationMillis) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                holdStartedAt = SystemClock.uptimeMillis()
                isHolding = true
                val up = waitForUpOrCancellation()
                val completed = up != null && SystemClock.uptimeMillis() - holdStartedAt >= holdDurationMillis
                isHolding = false
                holdProgress = 0f
                if (completed) currentOnClick()
            }
        }
        else -> Modifier.safeClickable { currentOnClick() }
    }

    Box(
        modifier = modifier
            .then(interactionModifier)
            .semantics(mergeDescendants = true) { role = Role.Button }
    ) {
        content(holdProgress)
    }
}

@Composable
fun HoldableActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requireHold: Boolean = false,
    holdDurationMillis: Long = 600L,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    textStyle: TextStyle = MaterialTheme.typography.labelLarge
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.55f)
    ) {
        HoldableActionArea(
            requireHold = requireHold,
            onClick = onClick,
            enabled = enabled,
            holdDurationMillis = holdDurationMillis,
            modifier = Modifier.fillMaxWidth()
        ) { holdProgress ->
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 64.dp, minHeight = 44.dp)
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text, style = textStyle, textAlign = TextAlign.Center)
                if (requireHold && holdProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { holdProgress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = contentColor,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}

@Composable
fun HoldToConfirmButton(
    actionText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError
) {
    HoldableActionButton(
        text = "Удерживать: $actionText",
        onClick = onConfirm,
        modifier = modifier,
        requireHold = true,
        holdDurationMillis = 700L,
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor
    )
}
