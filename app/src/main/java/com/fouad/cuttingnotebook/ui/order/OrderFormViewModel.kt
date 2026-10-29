package com.fouad.cuttingnotebook.ui.order

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fouad.cuttingnotebook.data.repo.ImageStore
import com.fouad.cuttingnotebook.data.repo.OrderDraft
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.data.repo.QuantityLine
import com.fouad.cuttingnotebook.domain.AppError
import com.fouad.cuttingnotebook.domain.Outcome
import com.fouad.cuttingnotebook.domain.PieceCountResult
import com.fouad.cuttingnotebook.domain.parseRequiredQty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable row in the quantities table. Kept as text so partial input is preserved. */
data class LineInput(
    val key: Long,
    val color: String = "",
    val size: String = "",
    val quantity: String = "",
)

data class OrderFormState(
    val orderId: Long? = null,
    val modelName: String = "",
    val modelCode: String = "",
    val notes: String = "",
    val imagePath: String? = null,
    val isDetailed: Boolean = true,
    val lines: List<LineInput> = listOf(LineInput(key = 0)),
    val totalInput: String = "",
    val error: AppError? = null,
    val saving: Boolean = false,
    val loaded: Boolean = false,
) {
    val isEditing: Boolean get() = orderId != null

    /** Running total under the table, so the operator can check it against the paper order. */
    val requiredTotal: Int
        get() = if (isDetailed) {
            lines.sumOf { it.quantity.toIntOrNull() ?: 0 }
        } else {
            totalInput.toIntOrNull() ?: 0
        }
}

/**
 * Backs both the new-order and the edit-order screen (spec 5.3).
 *
 * An edit reuses the same form, loads the order's rows into it, and hands the whole set back
 * to the repository, which matches rows by colour and size so recorded cuts stay attached.
 */
class OrderFormViewModel(
    private val repository: OrderRepository,
    private val images: ImageStore,
    private val orderId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(OrderFormState(orderId = orderId))
    val state: StateFlow<OrderFormState> = _state.asStateFlow()

    private var nextKey = 1L

    init {
        if (orderId != null) loadExisting(orderId) else _state.update { it.copy(loaded = true) }
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch {
            val header = repository.observeOrderHeader(id).first() ?: run {
                _state.update { it.copy(error = AppError.NotFound, loaded = true) }
                return@launch
            }
            val combinations = repository.combinationsOnce(id)
            val lines = combinations.mapIndexed { index, c ->
                LineInput(
                    key = index.toLong(),
                    color = c.color,
                    size = c.size,
                    quantity = c.required.toString(),
                )
            }
            nextKey = lines.size.toLong()
            _state.update {
                it.copy(
                    modelName = header.modelName,
                    modelCode = header.modelCode.orEmpty(),
                    notes = header.notes.orEmpty(),
                    imagePath = header.imagePath,
                    isDetailed = header.isDetailed,
                    lines = lines.ifEmpty { listOf(LineInput(key = 0)) },
                    totalInput = combinations.sumOf { c -> c.required }.toString(),
                    loaded = true,
                )
            }
        }
    }

    // ---- field edits --------------------------------------------------------------------

    fun setModelName(value: String) = _state.update { it.copy(modelName = value, error = null) }

    fun setModelCode(value: String) = _state.update { it.copy(modelCode = value) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun setDetailed(detailed: Boolean) = _state.update { it.copy(isDetailed = detailed, error = null) }

    fun setTotalInput(value: String) = _state.update { it.copy(totalInput = value, error = null) }

    fun updateLine(key: Long, transform: (LineInput) -> LineInput) = _state.update { state ->
        state.copy(
            lines = state.lines.map { if (it.key == key) transform(it) else it },
            error = null,
        )
    }

    fun addLine() = _state.update { state ->
        // A new row inherits the colour above it: orders usually list several sizes per colour.
        val lastColor = state.lines.lastOrNull()?.color.orEmpty()
        state.copy(lines = state.lines + LineInput(key = nextKey++, color = lastColor))
    }

    fun removeLine(key: Long) = _state.update { state ->
        if (state.lines.size <= 1) state
        else state.copy(lines = state.lines.filterNot { it.key == key }, error = null)
    }

    fun pickImage(uri: Uri) {
        viewModelScope.launch {
            val stored = images.importImage(uri)
            if (stored != null) _state.update { it.copy(imagePath = stored) }
        }
    }

    fun clearImage() = _state.update { it.copy(imagePath = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---- saving -------------------------------------------------------------------------

    fun save(onSaved: (Long) -> Unit, onError: (AppError) -> Unit) {
        val current = _state.value
        if (current.saving) return

        val draft = current.toDraft() ?: run {
            val error = AppError.InvalidQuantity
            _state.update { it.copy(error = error) }
            onError(error)
            return
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = if (current.orderId == null) {
                repository.createOrder(draft)
            } else {
                when (val update = repository.updateOrder(current.orderId, draft)) {
                    is Outcome.Failure -> Outcome.Failure(update.error)
                    is Outcome.Success -> Outcome.Success(current.orderId)
                }
            }
            _state.update { it.copy(saving = false) }
            when (result) {
                is Outcome.Success -> onSaved(result.value)
                is Outcome.Failure -> {
                    _state.update { it.copy(error = result.error) }
                    onError(result.error)
                }
            }
        }
    }

    private fun OrderFormState.toDraft(): OrderDraft? {
        val lines = if (isDetailed) {
            lines.mapNotNull { line ->
                // A row left completely blank is simply not part of the order.
                if (line.color.isBlank() && line.size.isBlank() && line.quantity.isBlank()) {
                    null
                } else {
                    when (val parsed = parseRequiredQty(line.quantity)) {
                        is PieceCountResult.Invalid -> return null
                        is PieceCountResult.Valid ->
                            QuantityLine(line.color.trim(), line.size.trim(), parsed.pieces)
                    }
                }
            }
        } else {
            when (val parsed = parseRequiredQty(totalInput)) {
                is PieceCountResult.Invalid -> return null
                is PieceCountResult.Valid -> listOf(QuantityLine("", "", parsed.pieces))
            }
        }
        if (lines.isEmpty()) return null

        return OrderDraft(
            modelName = modelName,
            modelCode = modelCode,
            imagePath = imagePath,
            orderNotes = notes,
            isDetailed = isDetailed,
            lines = lines,
        )
    }
}
