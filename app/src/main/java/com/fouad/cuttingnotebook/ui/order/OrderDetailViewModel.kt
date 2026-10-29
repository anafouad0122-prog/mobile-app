package com.fouad.cuttingnotebook.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fouad.cuttingnotebook.data.db.OperationDetailRow
import com.fouad.cuttingnotebook.data.db.OrderHeaderRow
import com.fouad.cuttingnotebook.data.db.OrderStatus
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.data.repo.QuantityLine
import com.fouad.cuttingnotebook.domain.AppError
import com.fouad.cuttingnotebook.domain.CombinationTotals
import com.fouad.cuttingnotebook.domain.OrderTotals
import com.fouad.cuttingnotebook.domain.Outcome
import com.fouad.cuttingnotebook.domain.orderTotals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OrderDetailState(
    val header: OrderHeaderRow? = null,
    val combinations: List<CombinationTotals> = emptyList(),
    val operations: List<OperationDetailRow> = emptyList(),
    val loading: Boolean = true,
) {
    val totals: OrderTotals get() = combinations.orderTotals()
    val isClosed: Boolean get() = header?.status == OrderStatus.CLOSED
}

/** Which modal the detail screen is showing, if any. */
sealed interface OrderDialog {
    data object None : OrderDialog
    data object Close : OrderDialog
    data object Reopen : OrderDialog
    data object AddQuantity : OrderDialog
}

class OrderDetailViewModel(
    private val repository: OrderRepository,
    private val orderId: Long,
) : ViewModel() {

    val state: StateFlow<OrderDetailState> = combine(
        repository.observeOrderHeader(orderId),
        repository.observeCombinations(orderId),
        repository.observeOperations(orderId),
    ) { header, combinations, operations ->
        OrderDetailState(header, combinations, operations, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailState())

    private val _dialog = MutableStateFlow<OrderDialog>(OrderDialog.None)
    val dialog: StateFlow<OrderDialog> = _dialog.asStateFlow()

    fun showDialog(dialog: OrderDialog) {
        _dialog.value = dialog
    }

    fun dismissDialog() {
        _dialog.value = OrderDialog.None
    }

    fun closeOrder(onDone: () -> Unit, onError: (AppError) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.closeOrder(orderId)) {
                is Outcome.Success -> {
                    _dialog.value = OrderDialog.None
                    onDone()
                }

                is Outcome.Failure -> onError(result.error)
            }
        }
    }

    fun reopenOrder(onDone: () -> Unit, onError: (AppError) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.reopenOrder(orderId)) {
                is Outcome.Success -> {
                    _dialog.value = OrderDialog.None
                    onDone()
                }

                is Outcome.Failure -> onError(result.error)
            }
        }
    }

    fun addQuantities(lines: List<QuantityLine>, onDone: () -> Unit, onError: (AppError) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.addQuantities(orderId, lines)) {
                is Outcome.Success -> {
                    _dialog.value = OrderDialog.None
                    onDone()
                }

                is Outcome.Failure -> onError(result.error)
            }
        }
    }
}
