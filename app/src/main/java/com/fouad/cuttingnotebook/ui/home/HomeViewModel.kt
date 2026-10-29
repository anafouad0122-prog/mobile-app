package com.fouad.cuttingnotebook.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.ui.model.OrderCardUi
import com.fouad.cuttingnotebook.ui.model.toCards
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val orders: List<OrderCardUi> = emptyList(),
    val loading: Boolean = true,
)

/**
 * The home list: open orders only (spec 5.2), filtered live by the search box.
 *
 * Totals come from the per-combination rows rather than a stored figure, so a cut recorded
 * on the next screen is reflected here without anything having to invalidate a cache.
 */
class HomeViewModel(repository: OrderRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = combine(
        repository.observeOpenOrders(),
        repository.observeAllCombinations(),
        _query,
    ) { headers, byOrder, query ->
        val cards = headers.toCards(byOrder)
        HomeUiState(orders = cards.filter { it.matches(query) }, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(value: String) {
        _query.value = value
    }
}

/** Matches the model, its code, or any colour or size on the order. */
internal fun OrderCardUi.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return modelName.contains(needle, ignoreCase = true) ||
        modelCode?.contains(needle, ignoreCase = true) == true ||
        colors.any { it.contains(needle, ignoreCase = true) } ||
        sizes.any { it.contains(needle, ignoreCase = true) }
}
