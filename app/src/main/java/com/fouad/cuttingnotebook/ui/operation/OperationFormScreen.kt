package com.fouad.cuttingnotebook.ui.operation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fouad.cuttingnotebook.data.db.EntryMethod
import com.fouad.cuttingnotebook.ui.Ar
import com.fouad.cuttingnotebook.ui.components.ConfirmDialog
import com.fouad.cuttingnotebook.ui.components.NumberField
import com.fouad.cuttingnotebook.ui.components.SectionCard
import com.fouad.cuttingnotebook.ui.components.TextFieldRow
import com.fouad.cuttingnotebook.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OperationFormScreen(
    viewModel: OperationFormViewModel,
    zone: ZoneId,
    onBack: () -> Unit,
    onSaved: (overflowed: Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val overflow by viewModel.overflow.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isEditing) Ar.OPERATION_EDIT_TITLE else Ar.OPERATION_NEW_TITLE)
                },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.combinations.size > 1) {
                SectionCard(title = Ar.COMBINATION) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.combinations.forEach { combination ->
                            FilterChip(
                                selected = combination.itemId == state.selectedItemId,
                                onClick = { viewModel.selectItem(combination.itemId) },
                                label = {
                                    Text(Ar.combinationLabel(combination.color, combination.size))
                                },
                            )
                        }
                    }
                }
            }

            state.selected?.let { selected ->
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = Ar.combinationLabel(selected.color, selected.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = Ar.remainingForCombination(selected.remaining),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected.remaining == 0) {
                                AppColors.done
                            } else {
                                AppColors.remaining
                            },
                        )
                    }
                    Text(
                        text = "${Ar.REQUIRED} ${selected.required} · ${Ar.CUT} ${selected.cut}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = Ar.METHOD) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.method == EntryMethod.DIRECT,
                        onClick = { viewModel.setMethod(EntryMethod.DIRECT) },
                        label = { Text(Ar.METHOD_DIRECT) },
                    )
                    FilterChip(
                        selected = state.method == EntryMethod.LAYERS,
                        onClick = { viewModel.setMethod(EntryMethod.LAYERS) },
                        label = { Text(Ar.METHOD_LAYERS) },
                    )
                }

                when (state.method) {
                    EntryMethod.DIRECT -> NumberField(
                        value = state.directInput,
                        onValueChange = viewModel::setDirectInput,
                        label = Ar.PIECES_CUT,
                        isError = state.countError != null,
                        supportingText = state.countError?.let(Ar::countError),
                    )

                    EntryMethod.LAYERS -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(
                                value = state.layersInput,
                                onValueChange = viewModel::setLayersInput,
                                label = Ar.LAYERS,
                                isError = state.countError != null,
                                modifier = Modifier.weight(1f),
                            )
                            NumberField(
                                value = state.perLayerInput,
                                onValueChange = viewModel::setPerLayerInput,
                                label = Ar.PIECES_PER_LAYER,
                                isError = state.countError != null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val layers = state.layersInput.toIntOrNull()
                        val perLayer = state.perLayerInput.toIntOrNull()
                        // The product is shown before saving so it can be checked (spec 5.5).
                        if (layers != null && perLayer != null && layers > 0 && perLayer > 0) {
                            Text(
                                text = Ar.layersResult(layers, perLayer, layers * perLayer),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (state.countError != null) {
                            Text(
                                text = Ar.countError(state.countError!!),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            SectionCard(title = Ar.OPERATION_TIME) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Ar.formatDateTime(state.performedAt, zone),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = { showDatePicker = true }) { Text(Ar.EDIT) }
                }
            }

            SectionCard(title = Ar.NOTES_OPTIONAL) {
                TextFieldRow(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    label = Ar.NOTES,
                    singleLine = false,
                    minLines = 2,
                )
            }

            Button(
                onClick = { viewModel.attemptSave(onSaved = onSaved, onError = onError) },
                enabled = !state.saving && state.selectedItemId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(Ar.SAVE)
            }

            Box(Modifier.height(16.dp))
        }
    }

    overflow?.let { check ->
        ConfirmDialog(
            title = Ar.OVERFLOW_TITLE,
            body = Ar.overflowBody(check.required, check.cutAfter, check.excessAfter),
            confirmLabel = Ar.OVERFLOW_CONFIRM,
            destructive = true,
            onConfirm = { viewModel.confirmOverflow(onSaved = onSaved, onError = onError) },
            onDismiss = viewModel::dismissOverflow,
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.performedAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            // The picker reports UTC midnight; only the date part is used.
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            viewModel.setDate(date, zone)
                        }
                        showDatePicker = false
                    },
                ) { Text(Ar.OK) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(Ar.CANCEL) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
