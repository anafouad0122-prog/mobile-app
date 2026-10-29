package com.fouad.cuttingnotebook.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fouad.cuttingnotebook.ui.Ar
import com.fouad.cuttingnotebook.ui.components.EmptyState
import com.fouad.cuttingnotebook.ui.components.OrderCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    imageFileFor: (String) -> File,
    onOpenOrder: (Long) -> Unit,
    onNewOrder: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Ar.HOME_TITLE) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = Ar.HISTORY)
                    }
                    IconButton(onClick = onOpenReports) {
                        Icon(Icons.Default.InsertChart, contentDescription = Ar.REPORTS)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = Ar.SETTINGS)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewOrder,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(Ar.NEW_ORDER) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        label = { Text(Ar.SEARCH_HINT) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.orders.isEmpty() && !state.loading) {
                    item {
                        EmptyState(
                            title = if (query.isBlank()) Ar.HOME_EMPTY_TITLE else Ar.HISTORY_EMPTY,
                            body = if (query.isBlank()) Ar.HOME_EMPTY_BODY else null,
                            modifier = Modifier.padding(top = 32.dp),
                        )
                    }
                }

                items(state.orders, key = { it.orderId }) { card ->
                    OrderCard(
                        card = card,
                        imageFileFor = imageFileFor,
                        onClick = { onOpenOrder(card.orderId) },
                    )
                }

                item { Box(Modifier.height(8.dp)) }
            }
        }
    }
}
