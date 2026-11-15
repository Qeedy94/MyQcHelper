package com.example.myapplication

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ReworkReasonPicker(
    visible: Boolean,
    sharedPrefs: SharedPreferences,
    multiSelect: Boolean,
    selectedReasons: Set<String> = emptySet(),
    onReasonClick: (String) -> Unit,
    onReasonDeleted: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null
) {
    var reworkReasons by remember(visible) {
        mutableStateOf(
            sharedPrefs.getStringSet(
                "rework_reasons",
                setOf("Брак", "Не тот цвет", "Плохое качество")
            ).orEmpty().toList()
        )
    }
    var newReasonText by remember(visible) { mutableStateOf("") }
    var reasonToDelete by remember(visible) { mutableStateOf<String?>(null) }

    if (reasonToDelete != null) {
        AlertDialog(
            onDismissRequest = { reasonToDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Удалить причину?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Удалить '${reasonToDelete!!}'?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                HoldToConfirmButton(
                    actionText = "Удалить",
                    onConfirm = {
                        val toDelete = reasonToDelete!!
                        reworkReasons = reworkReasons.filter { it != toDelete }
                        sharedPrefs.edit().putStringSet("rework_reasons", reworkReasons.toSet()).apply()
                        onReasonDeleted(toDelete)
                        reasonToDelete = null
                    }
                )
            },
            dismissButton = { TextButton(onClick = { reasonToDelete = null }) { Text("Отмена") } }
        )
    }

    AppBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = if (multiSelect) "Причины доработки" else "Причина доработки"
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth()) {
            items(reworkReasons, key = { it }) { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .safeCombinedClickable(
                            onTap = { onReasonClick(reason) },
                            onLongPress = { reasonToDelete = reason }
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (multiSelect) {
                        Checkbox(
                            checked = reason in selectedReasons,
                            onCheckedChange = null
                        )
                    } else {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(reason, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newReasonText,
                onValueChange = { newReasonText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Новая причина", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val reason = newReasonText.trim()
                    if (reason.isNotEmpty()) {
                        if (reason !in reworkReasons) {
                            reworkReasons = reworkReasons + reason
                            sharedPrefs.edit().putStringSet("rework_reasons", reworkReasons.toSet()).apply()
                        }
                        newReasonText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        if (multiSelect && onConfirm != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Отмена")
                }
                Button(
                    onClick = onConfirm,
                    enabled = selectedReasons.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Применить")
                }
            }
        }
    }
}
