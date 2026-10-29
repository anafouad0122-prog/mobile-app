package com.fouad.cuttingnotebook.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fouad.cuttingnotebook.data.db.OperationDetailRow
import com.fouad.cuttingnotebook.data.db.OrderStatus
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.ui.model.OrderCardUi
import com.fouad.cuttingnotebook.ui.model.toCards
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class HistoryTab { OPEN, CLOSED, OPERATIONS }

/** The date window the history is filtered to; null bounds mean "no limit". */
data class DateFilter(val from: LocalDate? = null, val to: LocalDate? = null) {
    val isActive: Boolean get() = from != null || to != null
}

data class HistoryQuery(
    val tab: HistoryTab = HistoryTab.OPEN,
    val text: String = "",
    val dates: DateFilter = DateFilter(),
)

/**
 * The history and search screen (spec 9).
 *
 * Open orders, closed orders and individual cut operations are kept as separate tabs so the
 * three are never confused with each other.
 */
class HistoryViewModel(
    private val repository: OrderRepository,
    private val zone: ZoneId,
) : ViewModel() {

    private val _query = MutableStateFlow(HistoryQuery())
    val query: StateFlow<HistoryQuery> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val orders: StateFlow<List<OrderCardUi>> = _query
        .flatMapLatest { q ->
            if (q.tab == HistoryTab.OPERATIONS) {
                flowOf(emptyList())
            } else {
                val (from, to) = q.dates.toMillisRange(zone)
                combine(
                    repository.searchOrders(
                        query = q.text,
                        status = if (q.tab == HistoryTab.OPEN) OrderStatus.OPEN else OrderStatus.CLOSED,
                        from = from,
                        to = to,
                    ),
                    repository.observeAllCombinations(),
                ) { headers, byOrder -> headers.toCards(byOrder) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val operations: StateFlow<List<OperationDetailRow>> = _query
        .flatMapLatest { q ->
            if (q.tab != HistoryTab.OPERATIONS) {
                flowOf(emptyList())
            } else {
                val (from, to) = q.dates.toMillisRange(zone)
                repository.searchOperations(q.text, from, to)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTab(tab: HistoryTab) = _query.update { it.copy(tab = tab) }

    fun setText(text: String) = _query.update { it.copy(text = text) }

    fun setDates(from: LocalDate?, to: LocalDate?) =
        _query.update { it.copy(dates = DateFilter(from, to)) }

    fun clearDates() = _query.update { it.copy(dates = DateFilter()) }

}

/** Open-ended on either side, so one bound can be set without the other. */
internal fun DateFilter.toMillisRange(zone: ZoneId): Pair<Long, Long> {
    val fromMillis = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
    val toMillis = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1)
        ?: Long.MAX_VALUE
    return fromMillis to toMillis
}
