package com.fouad.cuttingnotebook.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fouad.cuttingnotebook.data.settings.AccentColor
import com.fouad.cuttingnotebook.data.settings.AppSettings
import com.fouad.cuttingnotebook.data.settings.ThemeMode
import com.fouad.cuttingnotebook.ui.Ar
import com.fouad.cuttingnotebook.ui.components.ConfirmDialog
import com.fouad.cuttingnotebook.ui.components.SectionCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onMessage: (String, Boolean) -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.export(uri, onMessage) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.requestRestore(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Ar.SETTINGS_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = Ar.BACK)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (ui.busy) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Ar.BACKUP_IN_PROGRESS, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            SectionCard(title = Ar.SECTION_APPEARANCE) {
                Text(Ar.THEME, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(Ar.themeLabel(mode)) },
                        )
                    }
                }

                Text(Ar.ACCENT, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentColor.entries.forEach { accent ->
                        AccentSwatch(
                            accent = accent,
                            selected = settings.accent == accent,
                            onClick = { viewModel.setAccent(accent) },
                        )
                    }
                }
            }

            SectionCard(title = Ar.SECTION_SOUND) {
                SwitchRow(
                    title = Ar.SOUNDS_ENABLED,
                    subtitle = Ar.SOUNDS_ENABLED_HINT,
                    checked = settings.soundsEnabled,
                    onCheckedChange = viewModel::setSounds,
                )
                SwitchRow(
                    title = Ar.BUTTON_SOUNDS,
                    subtitle = Ar.BUTTON_SOUNDS_HINT,
                    checked = settings.buttonSoundsEnabled,
                    enabled = settings.soundsEnabled,
                    onCheckedChange = viewModel::setButtonSounds,
                )
                Text(
                    text = Ar.SILENT_MODE_NOTE,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = Ar.SECTION_SECURITY) {
                OutlinedButton(
                    onClick = viewModel::openPinDialog,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(Ar.CHANGE_PIN)
                }
            }

            SectionCard(title = Ar.SECTION_BACKUP) {
                Text(
                    text = Ar.EXPORT_BACKUP_HINT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { exportLauncher.launch(defaultBackupName()) },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(Ar.EXPORT_BACKUP)
                }

                Text(
                    text = Ar.IMPORT_BACKUP_HINT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        // Some file pickers label the zip differently, so both are accepted.
                        importLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream"),
                        )
                    },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(Ar.IMPORT_BACKUP)
                }
            }

            SectionCard(title = Ar.SECTION_ABOUT) {
                Text(Ar.ABOUT_BODY, style = MaterialTheme.typography.bodyMedium)
            }

            Box(Modifier.height(16.dp))
        }
    }

    if (ui.pendingRestore != null) {
        ConfirmDialog(
            title = Ar.RESTORE_WARNING_TITLE,
            body = Ar.RESTORE_WARNING_BODY,
            confirmLabel = Ar.RESTORE_CONFIRM,
            destructive = true,
            onConfirm = { viewModel.confirmRestore(onMessage) },
            onDismiss = viewModel::cancelRestore,
        )
    }

    if (ui.showPinDialog) {
        ChangePinDialog(
            error = ui.pinError,
            onClearError = viewModel::clearPinError,
            onConfirm = { current, new, confirmation ->
                viewModel.changePin(current, new, confirmation) { onMessage(it, true) }
            },
            onDismiss = viewModel::dismissPinDialog,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun AccentSwatch(accent: AccentColor, selected: Boolean, onClick: () -> Unit) {
    val color = accentPreviewColor(accent)
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color, CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/** Swatch colours mirror the light-theme primaries in the theme file. */
private fun accentPreviewColor(accent: AccentColor): Color = when (accent) {
    AccentColor.GREEN -> Color(0xFF1C6E5A)
    AccentColor.BLUE -> Color(0xFF1B5FA8)
    AccentColor.INDIGO -> Color(0xFF4A4A94)
    AccentColor.TEAL -> Color(0xFF00696E)
    AccentColor.AMBER -> Color(0xFF7C5800)
    AccentColor.CRIMSON -> Color(0xFFA12D3E)
}

@Composable
private fun ChangePinDialog(
    error: com.fouad.cuttingnotebook.data.settings.PinError?,
    onClearError: () -> Unit,
    onConfirm: (current: String, new: String, confirmation: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    ConfirmDialog(
        title = Ar.CHANGE_PIN,
        body = Ar.LOCK_SETUP_HINT,
        confirmLabel = Ar.SAVE,
        onConfirm = { onConfirm(current, new, confirmation) },
        onDismiss = onDismiss,
        extraContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PinField(Ar.PIN_CURRENT, current) { current = it; onClearError() }
                PinField(Ar.PIN_NEW, new) { new = it; onClearError() }
                PinField(Ar.PIN_CONFIRM, confirmation) { confirmation = it; onClearError() }
                if (error != null) {
                    Text(
                        text = Ar.pinError(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
    )
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(12)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun defaultBackupName(): String = "cutting-notebook-${LocalDate.now()}.zip"
