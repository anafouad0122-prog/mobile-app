package com.fouad.cuttingnotebook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fouad.cuttingnotebook.data.settings.AppSettings
import com.fouad.cuttingnotebook.data.settings.PinError
import com.fouad.cuttingnotebook.data.settings.PinManager
import com.fouad.cuttingnotebook.data.settings.SettingsStore
import com.fouad.cuttingnotebook.data.sound.Cue
import com.fouad.cuttingnotebook.data.sound.SoundPlayer
import com.fouad.cuttingnotebook.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Whether the app is showing the lock screen, and in which of its two modes. */
sealed interface LockState {
    /** Settings have not been read yet, so it is not known if a PIN exists. */
    data object Loading : LockState

    /** First run: the operator picks a PIN before any data is reachable. */
    data object NeedsSetup : LockState

    data object Locked : LockState

    data object Unlocked : LockState
}

/**
 * App-wide state: settings, the lock, and the sound player.
 *
 * Held above the navigation graph so the lock survives moving between screens and the
 * settings reach the theme without every screen passing them along.
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val settingsStore: SettingsStore = container.settings
    private val sounds: SoundPlayer = container.sounds

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _lock = MutableStateFlow<LockState>(LockState.Loading)
    val lock: StateFlow<LockState> = _lock.asStateFlow()

    private val _pinError = MutableStateFlow<PinError?>(null)
    val pinError: StateFlow<PinError?> = _pinError.asStateFlow()

    init {
        viewModelScope.launch { refreshLock() }
    }

    private suspend fun refreshLock() {
        _lock.value = if (settingsStore.storedVerifier() == null) {
            LockState.NeedsSetup
        } else {
            LockState.Locked
        }
    }

    /** Called when the app leaves the foreground, so coming back asks for the PIN again. */
    fun lockNow() {
        if (_lock.value == LockState.Unlocked) _lock.value = LockState.Locked
    }

    fun clearPinError() {
        _pinError.value = null
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            val verifier = settingsStore.storedVerifier()
            if (verifier == null) {
                _lock.value = LockState.NeedsSetup
                return@launch
            }
            // Hashing is deliberately slow; keep it off the main thread.
            val ok = withContext(Dispatchers.Default) { PinManager.verify(pin, verifier) }
            if (ok) {
                _pinError.value = null
                _lock.value = LockState.Unlocked
            } else {
                _pinError.value = PinError.WRONG
                play(Cue.ERROR)
            }
        }
    }

    fun setUpPin(pin: String, confirmation: String) {
        viewModelScope.launch {
            PinManager.validateFormat(pin)?.let {
                _pinError.value = it
                play(Cue.ERROR)
                return@launch
            }
            if (pin != confirmation) {
                _pinError.value = PinError.MISMATCH
                play(Cue.ERROR)
                return@launch
            }
            val verifier = withContext(Dispatchers.Default) { PinManager.createVerifier(pin) }
            settingsStore.saveVerifier(verifier)
            _pinError.value = null
            _lock.value = LockState.Unlocked
            play(Cue.SUCCESS)
        }
    }

    /** Used by Settings, and by the reopen confirmation, which asks for the PIN again. */
    suspend fun checkPin(pin: String): Boolean {
        val verifier = settingsStore.storedVerifier() ?: return false
        return withContext(Dispatchers.Default) { PinManager.verify(pin, verifier) }
    }

    fun play(cue: Cue) {
        sounds.play(cue, settings.value)
    }

    override fun onCleared() {
        sounds.release()
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(container) as T
        }
    }
}
