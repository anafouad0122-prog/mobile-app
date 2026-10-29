package com.fouad.cuttingnotebook.ui.order

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fouad.cuttingnotebook.ui.Ar
import com.fouad.cuttingnotebook.ui.components.NumberField
import com.fouad.cuttingnotebook.ui.components.SectionCard
import com.fouad.cuttingnotebook.ui.components.TextFieldRow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFormScreen(
    viewModel: OrderFormViewModel,
    imageFileFor: (String) -> File,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.pickImage(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) Ar.ORDER_EDIT_TITLE else Ar.ORDER_NEW_TITLE) },
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
            SectionCard(title = Ar.MODEL_NAME) {
                TextFieldRow(
                    value = state.modelName,
                    onValueChange = viewModel::setModelName,
                    label = Ar.MODEL_NAME,
                )
                TextFieldRow(
                    value = state.modelCode,
                    onValueChange = viewModel::setModelCode,
                    label = Ar.MODEL_CODE,
                )
                ImageRow(
                    imagePath = state.imagePath,
                    imageFileFor = imageFileFor,
                    onPick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onClear = viewModel::clearImage,
                )
            }

            SectionCard(title = Ar.BREAKDOWN_QUESTION) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.isDetailed,
                        onClick = { viewModel.setDetailed(true) },
                        label = { Text(Ar.BREAKDOWN_DETAILED) },
                    )
                    FilterChip(
                        selected = !state.isDetailed,
                        onClick = { viewModel.setDetailed(false) },
                        label = { Text(Ar.BREAKDOWN_TOTAL) },
                    )
                }
                Text(
                    text = if (state.isDetailed) {
                        Ar.BREAKDOWN_DETAILED_HINT
                    } else {
                        Ar.BREAKDOWN_TOTAL_HINT
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = Ar.QUANTITIES) {
                if (state.isDetailed) {
                    state.lines.forEach { line ->
                        QuantityLineRow(
                            line = line,
                            canRemove = state.lines.size > 1,
                            onChange = { transform -> viewModel.updateLine(line.key, transform) },
                            onRemove = { viewModel.removeLine(line.key) },
                        )
                    }
                    TextButton(onClick = viewModel::addLine) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(Ar.ADD_LINE, modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    NumberField(
                        value = state.totalInput,
                        onValueChange = viewModel::setTotalInput,
                        label = Ar.QUANTITY,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(Ar.TOTAL_REQUIRED, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.requiredTotal.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
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

            if (state.error != null) {
                Text(
                    text = Ar.appError(state.error!!),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = { viewModel.save(onSaved = onSaved, onError = {}) },
                enabled = !state.saving && state.modelName.isNotBlank() && state.requiredTotal > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(Ar.SAVE)
            }

            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuantityLineRow(
    line: LineInput,
    canRemove: Boolean,
    onChange: ((LineInput) -> LineInput) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextFieldRow(
            value = line.color,
            onValueChange = { value -> onChange { it.copy(color = value) } },
            label = Ar.COLOR,
            modifier = Modifier.weight(1.1f),
        )
        TextFieldRow(
            value = line.size,
            onValueChange = { value -> onChange { it.copy(size = value) } },
            label = Ar.SIZE,
            modifier = Modifier.weight(0.8f),
        )
        NumberField(
            value = line.quantity,
            onValueChange = { value -> onChange { it.copy(quantity = value) } },
            label = Ar.QUANTITY,
            modifier = Modifier.weight(0.9f),
        )
        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(
                Icons.Default.Delete,
                contentDescription = Ar.REMOVE_LINE,
                tint = if (canRemove) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

@Composable
private fun ImageRow(
    imagePath: String?,
    imageFileFor: (String) -> File,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val file = imagePath?.let(imageFileFor)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (file != null && file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = Ar.MODEL_IMAGE,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }

        OutlinedButton(onClick = onPick) {
            Text(if (imagePath == null) Ar.PICK_IMAGE else Ar.CHANGE_IMAGE)
        }

        if (imagePath != null) {
            TextButton(onClick = onClear) { Text(Ar.REMOVE_IMAGE) }
        }
    }
}
