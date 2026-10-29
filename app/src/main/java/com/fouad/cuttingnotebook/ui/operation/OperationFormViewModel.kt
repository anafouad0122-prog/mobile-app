package com.fouad.cuttingnotebook.ui.operation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fouad.cuttingnotebook.data.db.EntryMethod
import com.fouad.cuttingnotebook.data.repo.OperationDraft
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.domain.AppError
import com.fouad.cuttingnotebook.domain.CombinationTotals
import com.fouad.cuttingnotebook.domain.OverflowCheck
import com.fouad.cuttingnotebook.domain.Outcome
import com.fouad.cuttingnotebook.domain.PieceCountError
import com.fouad.cuttingnotebook.domain.PieceCountResult
import com.fouad.cuttingnotebook.domain.checkOverflow
import com.fouad.cuttingnotebook.domain.parseDirectCount
import com.fouad.cuttingnotebook.domain.parseLayerCount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OperationFormState(
    val operationId: Long? = null,
    val combinations: List<CombinationTotals> = emptyList(),
    val selectedItemId: Long? = null,
    val method: EntryMethod = EntryMethod.DIRECT,
    val directInput: String = "",
    val layersInput: String = "",
    val perLayerInput: String = "",
    val notes: String = "",
    val performedAt: Long = System.currentTimeMillis(),
    val countError: PieceCountError? = null,
    val saveError: AppError? = null,
    val saving: Boolean = false,
    val loaded: Boolean = false,
) {
    val isEditing: Boolean get() = operationId != null

    val selected: CombinationTotals?
        get() = combinations.firstOrNull { it.itemId == selectedItemId }

    /** The piece count the current inputs come to, or null while they are incomplete. */
    val parsedPieces: Int?
        get() = when (val result = parseCurrent()) {
            is PieceCountResult.Valid -> result.pieces
            else -> null
        }

    fun parseCurrent(): PieceCountResult = when (method) {
        EntryMethod.DIRECT -> parseDirectCount(directInput)
        EntryMethod.LAYERS -> parseLayerCount(layersInput, perLayerInput)
    }
}

/**
 * The cut-recording screen (spec 5.5), used for new operations and for editing existing ones.
 *
 * An edit is checked against the same overflow rule as a new entry, with the operation's own
 * current figure taken out of the comparison first, so correcting 10 to 8 never looks like
 * an overrun.
 */
class OperationFormViewModel(
    private val repository: OrderRepository,
    private val orderId: Long,
    private val operationId: Long?,
    initialItemId: Long?,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OperationFormState(
            operationId = operationId,
            selectedItemId = initialItemId,
            performedAt = now(),
        ),
    )
    val state: StateFlow<OperationFormState> = _state.asStateFlow()

    private val _overflow = MutableStateFlow<OverflowCheck?>(null)
    val overflow: StateFlow<OverflowCheck?> = _overflow.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val combinations = repository.combinationsOnce(orderId)
        if (operationId == null) {
            _state.update {
                it.copy(
                    combinations = combinations,
                    selectedItemId = it.selectedItemId
                        ?: combinations.firstOrNull { c -> !c.isComplete }?.itemId
                        ?: combinations.firstOrNull()?.itemId,
                    loaded = true,
                )
            }
            return
        }

        val existing = repository.observeOperation(operationId).first()
        if (existing == null) {
            _state.update { it.copy(combinations = combinations, loaded = true) }
            return
        }
        _state.update {
            it.copy(
                combinations = combinations,
                selectedItemId = existing.orderItemId,
                method = existing.entryMethod,
                directInput = if (existing.entryMethod == EntryMethod.DIRECT) {
                    existing.pieces.toString()
                } else {
                    ""
                },
                layersInput = existing.layers?.toString().orEmpty(),
                perLayerInput = existing.piecesPerLayer?.toString().orEmpty(),
                notes = existing.notes.orEmpty(),
                performedAt = existing.performedAt,
                loaded = true,
            )
        }
    }

    // ---- field edits --------------------------------------------------------------------

    fun selectItem(itemId: Long) = _state.update { it.copy(selectedItemId = itemId, saveError = null) }

    fun setMethod(method: EntryMethod) = _state.update { it.copy(method = method, countError = null) }

    fun setDirectInput(value: String) = _state.update { it.copy(directInput = value, countError = null) }

    fun setLayersInput(value: String) = _state.update { it.copy(layersInput = value, countError = null) }

    fun setPerLayerInput(value: String) = _state.update { it.copy(perLayerInput = value, countError = null) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    /** Keeps the recorded time of day and moves the operation to another date. */
    fun setDate(date: LocalDate, zone: ZoneId) = _state.update { state ->
        val current = Instant.ofEpochMilli(state.performedAt).atZone(zone)
        state.copy(
            performedAt = current.with(date).toInstant().toEpochMilli(),
        )
    }

    fun dismissOverflow() {
        _overflow.value = null
    }

    // ---- saving -------------------------------------------------------------------------

    /**
     * Validates, then either asks for confirmation because the entry runs past the required
     * quantity, or saves straight away.
     */
    fun attemptSave(onSaved: (overflowed: Boolean) -> Unit, onError: (String) -> Unit) {
        val current = _state.value
        if (current.saving) return

        val itemId = current.selectedItemId ?: run {
            _state.update { it.copy(saveError = AppError.NotFound) }
            return
        }
        val pieces = when (val parsed = current.parseCurrent()) {
            is PieceCountResult.Invalid -> {
                _state.update { it.copy(countError = parsed.reason) }
                return
            }

            is PieceCountResult.Valid -> parsed.pieces
        }

        viewModelScope.launch {
            val combination = repository.combinationsOnce(orderId).firstOrNull { it.itemId == itemId }
            if (combination == null) {
                _state.update { it.copy(saveError = AppError.NotFound) }
                return@launch
            }
            val baseline = repository.cutTotalExcluding(itemId, operationId)
            val check = checkOverflow(combination.required, baseline, pieces)
            if (check.overflows) {
                _overflow.value = check
            } else {
                commit(itemId, pieces, overflowed = false, onSaved = onSaved, onError = onError)
            }
        }
    }

    /** Called after the operator confirms the overflow warning. */
    fun confirmOverflow(onSaved: (overflowed: Boolean) -> Unit, onError: (String) -> Unit) {
        val current = _state.value
        val itemId = current.selectedItemId ?: return
        val pieces = current.parsedPieces ?: return
        _overflow.value = null
        viewModelScope.launch {
            commit(itemId, pieces, overflowed = true, onSaved = onSaved, onError = onError)
        }
    }

    private suspend fun commit(
        itemId: Long,
        pieces: Int,
        overflowed: Boolean,
        onSaved: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        val current = _state.value
        _state.update { it.copy(saving = true) }

        val draft = OperationDraft(
            orderItemId = itemId,
            pieces = pieces,
            entryMethod = current.method,
            layers = current.layersInput.toIntOrNull().takeIf { current.method == EntryMethod.LAYERS },
            piecesPerLayer = current.perLayerInput.toIntOrNull()
                .takeIf { current.method == EntryMethod.LAYERS },
            performedAt = current.performedAt,
            notes = current.notes,
        )

        val result = if (operationId == null) {
            when (val outcome = repository.recordOperation(orderId, draft)) {
                is Outcome.Failure -> Outcome.Failure(outcome.error)
                is Outcome.Success -> Outcome.Success(Unit)
            }
        } else {
            repository.updateOperation(operationId, draft)
        }

        _state.update { it.copy(saving = false) }
        when (result) {
            is Outcome.Success -> onSaved(overflowed)
            is Outcome.Failure -> {
                _state.update { it.copy(saveError = result.error) }
                onError(com.fouad.cuttingnotebook.ui.Ar.appError(result.error))
            }
        }
    }

    /** True once every combination on the order is at or past its required quantity. */
    suspend fun orderIsComplete(): Boolean {
        val combinations = repository.combinationsOnce(orderId)
        return combinations.isNotEmpty() && combinations.all { it.isComplete }
    }
}
