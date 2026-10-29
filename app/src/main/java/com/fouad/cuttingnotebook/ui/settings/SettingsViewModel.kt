package com.fouad.cuttingnotebook.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fouad.cuttingnotebook.data.backup.BackupManager
import com.fouad.cuttingnotebook.data.backup.BackupResult
import com.fouad.cuttingnotebook.data.settings.AccentColor
import com.fouad.cuttingnotebook.data.settings.PinError
import com.fouad.cuttingnotebook.data.settings.PinManager
import com.fouad.cuttingnotebook.data.settings.SettingsStore
import com.fouad.cuttingnotebook.data.settings.ThemeMode
import com.fouad.cuttingnotebook.ui.Ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val busy: Boolean = false,
    val showPinDialog: Boolean = false,
    val pinError: PinError? = null,
    val pendingRestore: Uri? = null,
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val backups: BackupManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }

    fun setAccent(accent: AccentColor) = viewModelScope.launch { settingsStore.setAccent(accent) }

    fun setSounds(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setSoundsEnabled(enabled) }

    fun setButtonSounds(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setButtonSoundsEnabled(enabled) }

    // ---- PIN ---------------------------------------------------------------------------

    fun openPinDialog() = _ui.update { it.copy(showPinDialog = true, pinError = null) }

    fun dismissPinDialog() = _ui.update { it.copy(showPinDialog = false, pinError = null) }

    fun clearPinError() = _ui.update { it.copy(pinError = null) }

    fun changePin(current: String, new: String, confirmation: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val verifier = settingsStore.storedVerifier()
            if (verifier == null) {
                _ui.update { it.copy(pinError = PinError.NOT_SET) }
                return@launch
            }
            val currentOk = withContext(Dispatchers.Default) {
                PinManager.verify(current, verifier)
            }
            if (!currentOk) {
                _ui.update { it.copy(pinError = PinError.WRONG_CURRENT) }
                return@launch
            }
            PinManager.validateFormat(new)?.let { error ->
                _ui.update { it.copy(pinError = error) }
                return@launch
            }
            if (new != confirmation) {
                _ui.update { it.copy(pinError = PinError.MISMATCH) }
                return@launch
            }
            val updated = withContext(Dispatchers.Default) { PinManager.createVerifier(new) }
            settingsStore.saveVerifier(updated)
            _ui.update { it.copy(showPinDialog = false, pinError = null) }
            onDone(Ar.PIN_CHANGED)
        }
    }

    // ---- backup ------------------------------------------------------------------------

    fun export(destination: Uri, onResult: (message: String, success: Boolean) -> Unit) {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = backups.export(destination)
            _ui.update { it.copy(busy = false) }
            onResult(result.message(), result !is BackupResult.Failed)
        }
    }

    /** A restore is confirmed first: it replaces everything currently on the phone. */
    fun requestRestore(source: Uri) = _ui.update { it.copy(pendingRestore = source) }

    fun cancelRestore() = _ui.update { it.copy(pendingRestore = null) }

    fun confirmRestore(onResult: (message: String, success: Boolean) -> Unit) {
        val source = _ui.value.pendingRestore ?: return
        _ui.update { it.copy(busy = true, pendingRestore = null) }
        viewModelScope.launch {
            val result = backups.restore(source)
            _ui.update { it.copy(busy = false) }
            onResult(result.message(), result !is BackupResult.Failed)
        }
    }
}

private fun BackupResult.message(): String = when (this) {
    is BackupResult.Exported -> Ar.backupExported(models, orders, operations, images)
    is BackupResult.Restored -> Ar.backupRestored(models, orders, operations, images)
    is BackupResult.Failed -> Ar.backupError(reason)
}
