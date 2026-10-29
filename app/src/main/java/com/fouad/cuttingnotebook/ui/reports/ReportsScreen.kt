package com.fouad.cuttingnotebook.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fouad.cuttingnotebook.data.db.ReportBucketRow
import com.fouad.cuttingnotebook.domain.ReportPeriod
import com.fouad.cuttingnotebook.ui.Ar
import com.fouad.cuttingnotebook.ui.components.SectionCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Ar.REPORTS_TITLE) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { viewModel.setPeriod(period) },
                        label = { Text(Ar.periodLabel(period)) },
                    )
                }
            }

            if (state.period != ReportPeriod.ALL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // In an RTL layout the back-pointing arrow sits on the right, so the
                    // "previous period" control is the one the eye reaches first.
                    IconButton(onClick = { viewModel.step(-1) }) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = Ar.PREVIOUS_PERIOD,
                        )
                    }
                    Text(
                        text = state.range?.let { Ar.rangeLabel(it.fromDate, it.toDate) }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.step(1) },
                        enabled = viewModel.canStepForward(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Ar.NEXT_PERIOD,
                        )
                    }
                }
            } else {
                Text(
                    text = state.range?.let { Ar.rangeLabel(it.fromDate, it.toDate) }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.pieces.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = Ar.TOTAL_PIECES,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${Ar.OPERATIONS_COUNT}: ${state.operations}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.operations == 0) {
                Text(
                    text = Ar.REPORT_EMPTY,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SectionCard(title = Ar.BY_MODEL) {
                    state.byModel.forEachIndexed { index, bucket ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            )
                        }
                        BucketRow(label = bucket.label, bucket = bucket)
                    }
                }

                if (state.period != ReportPeriod.DAY) {
                    SectionCard(title = Ar.BY_DAY) {
                        state.byDay.forEachIndexed { index, bucket ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                )
                            }
                            BucketRow(label = bucket.label.toArabicDate(), bucket = bucket)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketRow(label: String, bucket: ReportBucketRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${Ar.OPERATIONS_COUNT}: ${bucket.operations}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = bucket.pieces.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** SQLite hands back "yyyy-MM-dd"; show it the way the rest of the app shows dates. */
private fun String.toArabicDate(): String =
    runCatching { Ar.formatDate(LocalDate.parse(this)) }.getOrDefault(this)
