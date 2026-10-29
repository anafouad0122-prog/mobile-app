package com.fouad.cuttingnotebook.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fouad.cuttingnotebook.data.settings.PinError
import com.fouad.cuttingnotebook.data.settings.PinManager
import com.fouad.cuttingnotebook.ui.Ar

/**
 * The lock screen, in both of its modes: asking for the PIN, and choosing one on first run
 * (spec 5.1).
 */
@Composable
fun LockScreen(
    isSetup: Boolean,
    error: PinError?,
    onSubmit: (pin: String) -> Unit,
    onSetUp: (pin: String, confirmation: String) -> Unit,
    onInputChanged: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    fun submit() {
        if (isSetup) onSetUp(pin, confirmation) else onSubmit(pin)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = Ar.APP_NAME,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(40.dp))

            Text(
                text = if (isSetup) Ar.LOCK_SETUP_TITLE else Ar.LOCK_TITLE,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )

            if (isSetup) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Ar.LOCK_SETUP_HINT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(28.dp))

            val fieldModifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(PinManager.MAX_LENGTH)
                    onInputChanged()
                },
                label = { Text(if (isSetup) Ar.PIN_NEW else Ar.PIN) },
                singleLine = true,
                isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = if (isSetup) ImeAction.Next else ImeAction.Done,
                ),
                modifier = fieldModifier,
            )

            if (isSetup) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it.filter(Char::isDigit).take(PinManager.MAX_LENGTH)
                        onInputChanged()
                    },
                    label = { Text(Ar.PIN_CONFIRM) },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = fieldModifier,
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = Ar.pinError(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = ::submit,
                enabled = pin.length >= PinManager.MIN_LENGTH &&
                    (!isSetup || confirmation.length >= PinManager.MIN_LENGTH),
                modifier = fieldModifier.height(56.dp),
            ) {
                Text(if (isSetup) Ar.SAVE else Ar.UNLOCK)
            }
        }
    }
}
