package com.fouad.cuttingnotebook.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fouad.cuttingnotebook.data.db.OperationDetailRow
import com.fouad.cuttingnotebook.ui.Ar
import com.fouad.cuttingnotebook.ui.components.EmptyState
import com.fouad.cuttingnotebook.ui.components.OrderCard
import java.io.File
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    imageFileFor: (String) -> File,
    zone: ZoneId,
    onBack: () -> Unit,
    onOpenOrder: (Long) -> Unit,
) {
    val query by viewModel.query.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val operations by viewModel.operations.collectAsState()
    var showDateRange by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Ar.HISTORY_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = Ar.BACK)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = query.tab.ordinal) {
                HistoryTab.entries.forEach { tab ->
                    Tab(
                        selected = query.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = { Text(tab.label()) },
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query.text,
                    onValueChange = viewModel::setText,
                    label = { Text(Ar.SEARCH_HINT) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { showDateRange = true }) {
                        Text(
                            if (query.dates.isActive) {
                                Ar.rangeLabel(
                                    query.dates.from ?: query.dates.to!!,
                                    query.dates.to ?: query.dates.from!!,
                                )
                            } else {
                                Ar.FILTER_DATE
                            },
                        )
                    }
                    if (query.dates.isActive) {
                        TextButton(onClick = viewModel::clearDates) { Text(Ar.CLEAR_DATES) }
                    }
                }
            }

            val showingOperations = query.tab == HistoryTab.OPERATIONS
            val isEmpty = if (showingOperations) operations.isEmpty() else orders.isEmpty()

            if (isEmpty) {
                EmptyState(title = Ar.HISTORY_EMPTY, modifier = Modifier.padding(top = 24.dp))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showingOperations) {
                        items(operations, key = { it.id }) { operation ->
                            OperationHistoryCard(
                                operation = operation,
                                zone = zone,
                                onClick = { onOpenOrder(operation.orderId) },
                            )
                        }
                    } else {
                        items(orders, key = { it.orderId }) { card ->
                            OrderCard(
                                card = card,
                                imageFileFor = imageFileFor,
                                onClick = { onOpenOrder(card.orderId) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDateRange) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRange = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // The picker reports UTC midnight; only the date part is used.
                        val utc = ZoneId.of("UTC")
                        val from = pickerState.selectedStartDateMillis
                            ?.let { Instant.ofEpochMilli(it).atZone(utc).toLocalDate() }
                        val to = pickerState.selectedEndDateMillis
                            ?.let { Instant.ofEpochMilli(it).atZone(utc).toLocalDate() }
                        viewModel.setDates(from, to ?: from)
                        showDateRange = false
                    },
                ) { Text(Ar.OK) }
            },
            dismissButton = {
                TextButton(onClick = { showDateRange = false }) { Text(Ar.CANCEL) }
            },
        ) {
            DateRangePicker(state = pickerState)
        }
    }
}

@Composable
private fun OperationHistoryCard(
    operation: OperationDetailRow,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(operation.modelName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = Ar.combinationLabel(operation.color, operation.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Ar.formatDateTime(operation.performedAt, zone),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = operation.pieces.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = Ar.PIECES,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun HistoryTab.label(): String = when (this) {
    HistoryTab.OPEN -> Ar.TAB_OPEN
    HistoryTab.CLOSED -> Ar.TAB_CLOSED
    HistoryTab.OPERATIONS -> Ar.TAB_OPERATIONS
}
