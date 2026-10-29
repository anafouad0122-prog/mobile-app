package com.fouad.cuttingnotebook.ui.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import coil.compose.AsyncImage
import com.fouad.cuttingnotebook.data.db.EntryMethod
import com.fouad.cuttingnotebook.data.db.OperationDetailRow
import com.fouad.cuttingnotebook.data.repo.QuantityLine
import com.fouad.cuttingnotebook.domain.CombinationTotals
import com.fouad.cuttingnotebook.ui.Ar
import com.fouad.cuttingnotebook.ui.components.ConfirmDialog
import com.fouad.cuttingnotebook.ui.components.DetailRow
import com.fouad.cuttingnotebook.ui.components.EmptyState
import com.fouad.cuttingnotebook.ui.components.NumberField
import com.fouad.cuttingnotebook.ui.components.Pill
import com.fouad.cuttingnotebook.ui.components.SectionCard
import com.fouad.cuttingnotebook.ui.components.StatBlock
import com.fouad.cuttingnotebook.ui.components.TextFieldRow
import com.fouad.cuttingnotebook.ui.theme.AppColors
import java.io.File
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    viewModel: OrderDetailViewModel,
    imageFileFor: (String) -> File,
    zone: ZoneId,
    checkPin: suspend (String) -> Boolean,
    onBack: () -> Unit,
    onEditOrder: () -> Unit,
    onRecordCut: (itemId: Long?) -> Unit,
    onEditOperation: (Long) -> Unit,
    onMessage: (String, Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val dialog by viewModel.dialog.collectAsState()
    val header = state.header

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(header?.modelName ?: Ar.ORDER_DETAIL_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = Ar.BACK)
                    }
                },
                actions = {
                    if (!state.isClosed) {
                        IconButton(onClick = onEditOrder) {
                            Icon(Icons.Default.Edit, contentDescription = Ar.EDIT)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isClosed && header != null) {
                ExtendedFloatingActionButton(
                    onClick = { onRecordCut(null) },
                    icon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                    text = { Text(Ar.RECORD_CUT) },
                )
            }
        },
    ) { padding ->
        if (header == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (!state.loading) EmptyState(title = Ar.appError(com.fouad.cuttingnotebook.domain.AppError.NotFound))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard(
                modelName = header.modelName,
                modelCode = header.modelCode,
                imagePath = header.imagePath,
                imageFileFor = imageFileFor,
                notes = header.notes,
                createdAt = header.createdAt,
                closedAt = header.closedAt,
                isClosed = state.isClosed,
                zone = zone,
            )

            SectionCard(title = Ar.TOTAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatBlock(Ar.REQUIRED, state.totals.required)
                    StatBlock(Ar.CUT, state.totals.cut, MaterialTheme.colorScheme.primary)
                    StatBlock(
                        label = Ar.REMAINING,
                        value = state.totals.remaining,
                        color = if (state.totals.remaining == 0) AppColors.done else AppColors.remaining,
                    )
                    if (state.totals.excess > 0) {
                        StatBlock(Ar.EXCESS, state.totals.excess, AppColors.excess)
                    }
                }
                if (state.totals.required > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (state.totals.cut.toFloat() / state.totals.required).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                    )
                }
            }

            SectionCard(title = Ar.QUANTITIES) {
                state.combinations.forEach { combination ->
                    CombinationRow(
                        combination = combination,
                        enabled = !state.isClosed,
                        onClick = { onRecordCut(combination.itemId) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    )
                }
                if (!state.isClosed) {
                    TextButton(onClick = { viewModel.showDialog(OrderDialog.AddQuantity) }) {
                        Text(Ar.ADD_QUANTITY)
                    }
                }
            }

            SectionCard(title = Ar.OPERATIONS_LOG) {
                if (state.operations.isEmpty()) {
                    Text(
                        text = Ar.NO_OPERATIONS,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.operations.forEach { operation ->
                        OperationRow(
                            operation = operation,
                            zone = zone,
                            enabled = !state.isClosed,
                            onClick = { onEditOperation(operation.id) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        )
                    }
                }
            }

            if (state.isClosed) {
                Text(
                    text = Ar.ORDER_CLOSED_NOTICE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { viewModel.showDialog(OrderDialog.Reopen) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(Ar.REOPEN_ORDER)
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.showDialog(OrderDialog.Close) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(Ar.CLOSE_ORDER)
                }
            }

            Box(Modifier.height(72.dp))
        }
    }

    when (dialog) {
        OrderDialog.None -> Unit

        OrderDialog.Close -> ConfirmDialog(
            title = Ar.CLOSE_ORDER_TITLE,
            body = buildString {
                append(Ar.CLOSE_ORDER_INTRO)
                append("\n\n")
                append("${Ar.REQUIRED}: ${state.totals.required}\n")
                append("${Ar.CUT}: ${state.totals.cut}\n")
                append("${Ar.REMAINING}: ${state.totals.remaining}")
                if (state.totals.excess > 0) append("\n${Ar.EXCESS}: ${state.totals.excess}")
                if (state.totals.remaining > 0) {
                    append("\n\n")
                    append(Ar.CLOSE_ORDER_HAS_REMAINING)
                }
            },
            confirmLabel = Ar.CLOSE_ORDER_CONFIRM,
            destructive = state.totals.remaining > 0,
            onConfirm = {
                viewModel.closeOrder(
                    onDone = { onMessage(Ar.ORDER_CLOSED_DONE, true) },
                    onError = { onMessage(Ar.appError(it), false) },
                )
            },
            onDismiss = viewModel::dismissDialog,
        )

        OrderDialog.Reopen -> ReopenDialog(
            checkPin = checkPin,
            onConfirmed = {
                viewModel.reopenOrder(
                    onDone = { onMessage(Ar.ORDER_REOPENED, true) },
                    onError = { onMessage(Ar.appError(it), false) },
                )
            },
            onDismiss = viewModel::dismissDialog,
        )

        OrderDialog.AddQuantity -> AddQuantityDialog(
            // The dialog is only reachable from a loaded order; detailed is the safe default.
            isDetailed = header?.isDetailed ?: true,
            onConfirm = { line ->
                viewModel.addQuantities(
                    lines = listOf(line),
                    onDone = { onMessage(Ar.QUANTITY_ADDED, true) },
                    onError = { onMessage(Ar.appError(it), false) },
                )
            },
            onDismiss = viewModel::dismissDialog,
        )
    }
}

@Composable
private fun HeaderCard(
    modelName: String,
    modelCode: String?,
    imagePath: String?,
    imageFileFor: (String) -> File,
    notes: String?,
    createdAt: Long,
    closedAt: Long?,
    isClosed: Boolean,
    zone: ZoneId,
) {
    SectionCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val file = imagePath?.let(imageFileFor)
            if (file != null && file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = Ar.MODEL_IMAGE,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else if (imagePath != null) {
                Text(
                    text = Ar.IMAGE_MISSING,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(modelName, style = MaterialTheme.typography.headlineSmall)
                if (!modelCode.isNullOrBlank()) {
                    Text(
                        text = modelCode,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(
                    text = if (isClosed) Ar.STATUS_CLOSED else Ar.STATUS_OPEN,
                    container = if (isClosed) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    content = if (isClosed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }

        DetailRow(Ar.CREATED_AT, Ar.formatDateTime(createdAt, zone))
        if (closedAt != null) DetailRow(Ar.CLOSED_AT, Ar.formatDateTime(closedAt, zone))
        if (!notes.isNullOrBlank()) {
            DetailRow(Ar.NOTES, notes)
        }
    }
}

@Composable
private fun CombinationRow(
    combination: CombinationTotals,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Ar.combinationLabel(combination.color, combination.size),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${Ar.REQUIRED} ${combination.required} · ${Ar.CUT} ${combination.cut}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = combination.remaining.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = if (combination.remaining == 0) AppColors.done else AppColors.remaining,
            )
            Text(
                text = Ar.REMAINING,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (combination.excess > 0) {
                Text(
                    text = "${Ar.EXCESS} ${combination.excess}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.excess,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun OperationRow(
    operation: OperationDetailRow,
    zone: ZoneId,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Ar.combinationLabel(operation.color, operation.size),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = Ar.formatDateTime(operation.performedAt, zone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (operation.entryMethod == EntryMethod.LAYERS &&
                operation.layers != null &&
                operation.piecesPerLayer != null
            ) {
                Text(
                    text = Ar.layersResult(
                        operation.layers,
                        operation.piecesPerLayer,
                        operation.pieces,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!operation.notes.isNullOrBlank()) {
                Text(
                    text = operation.notes,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (operation.updatedAt > operation.createdAt) {
                Text(
                    text = Ar.EDITED_MARK,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.excess,
                )
            }
        }
        Text(
            text = operation.pieces.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReopenDialog(
    checkPin: suspend (String) -> Boolean,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ConfirmDialog(
        title = Ar.REOPEN_TITLE,
        body = Ar.REOPEN_BODY,
        confirmLabel = Ar.REOPEN_CONFIRM,
        onConfirm = {
            scope.launch { if (checkPin(pin)) onConfirmed() else wrong = true }
        },
        onDismiss = onDismiss,
        extraContent = {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(12)
                    wrong = false
                },
                label = { Text(Ar.PIN) },
                singleLine = true,
                isError = wrong,
                supportingText = if (wrong) {
                    { Text(Ar.PIN_WRONG, color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun AddQuantityDialog(
    isDetailed: Boolean,
    onConfirm: (QuantityLine) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }

    ConfirmDialog(
        title = Ar.ADD_QUANTITY_TITLE,
        body = Ar.ADD_QUANTITY_HINT,
        confirmLabel = Ar.SAVE,
        onConfirm = {
            val parsed = quantity.toIntOrNull() ?: return@ConfirmDialog
            if (parsed <= 0) return@ConfirmDialog
            onConfirm(QuantityLine(color.trim(), size.trim(), parsed))
        },
        onDismiss = onDismiss,
        extraContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isDetailed) {
                    TextFieldRow(value = color, onValueChange = { color = it }, label = Ar.COLOR)
                    TextFieldRow(value = size, onValueChange = { size = it }, label = Ar.SIZE)
                }
                NumberField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = Ar.QUANTITY,
                )
            }
        },
    )
}

