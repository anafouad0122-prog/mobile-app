package com.fouad.cuttingnotebook.data.repo

import androidx.room.withTransaction
import com.fouad.cuttingnotebook.data.db.CutOperationEntity
import com.fouad.cuttingnotebook.data.db.CutOrderEntity
import com.fouad.cuttingnotebook.data.db.CuttingDatabase
import com.fouad.cuttingnotebook.data.db.EntryMethod
import com.fouad.cuttingnotebook.data.db.ModelEntity
import com.fouad.cuttingnotebook.data.db.OperationDetailRow
import com.fouad.cuttingnotebook.data.db.OrderHeaderRow
import com.fouad.cuttingnotebook.data.db.OrderItemEntity
import com.fouad.cuttingnotebook.data.db.OrderItemTotalsRow
import com.fouad.cuttingnotebook.data.db.OrderStatus
import com.fouad.cuttingnotebook.domain.AppError
import com.fouad.cuttingnotebook.domain.CombinationTotals
import com.fouad.cuttingnotebook.domain.MAX_PIECES_PER_OPERATION
import com.fouad.cuttingnotebook.domain.Outcome
import com.fouad.cuttingnotebook.domain.outcomeCatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A colour+size line as the order form hands it over. */
data class QuantityLine(
    val color: String,
    val size: String,
    val requiredQty: Int,
)

/**
 * Everything needed to create or update an order's model and quantities.
 *
 * Notes belong to the order rather than the model: the same model comes back on a later
 * order with different instructions, and those should not overwrite the old ones.
 */
data class OrderDraft(
    val modelName: String,
    val modelCode: String?,
    val imagePath: String?,
    val orderNotes: String?,
    val isDetailed: Boolean,
    val lines: List<QuantityLine>,
)

data class OperationDraft(
    val orderItemId: Long,
    val pieces: Int,
    val entryMethod: EntryMethod,
    val layers: Int?,
    val piecesPerLayer: Int?,
    val performedAt: Long,
    val notes: String?,
)

/**
 * The single writer for orders, quantities and cut operations.
 *
 * Reads expose the raw per-combination rows; the clamping and summing live in
 * [com.fouad.cuttingnotebook.domain] so the same arithmetic backs the screens, the close
 * dialog and the reports.
 */
class OrderRepository(
    private val db: CuttingDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val models = db.modelDao()
    private val orders = db.orderDao()
    private val items = db.orderItemDao()
    private val operations = db.operationDao()

    // ---- reads -------------------------------------------------------------------------

    fun observeOpenOrders(): Flow<List<OrderHeaderRow>> =
        orders.observeHeadersByStatus(OrderStatus.OPEN)

    fun observeClosedOrders(): Flow<List<OrderHeaderRow>> =
        orders.observeHeadersByStatus(OrderStatus.CLOSED)

    fun observeOrderHeader(orderId: Long): Flow<OrderHeaderRow?> = orders.observeHeader(orderId)

    fun observeCombinations(orderId: Long): Flow<List<CombinationTotals>> =
        items.observeTotalsForOrder(orderId).map { rows -> rows.map { it.toCombination() } }

    fun observeAllCombinations(): Flow<Map<Long, List<CombinationTotals>>> =
        items.observeAllTotals().map { rows ->
            rows.groupBy({ it.orderId }, { it.toCombination() })
        }

    fun observeOperations(orderId: Long): Flow<List<OperationDetailRow>> =
        operations.observeForOrder(orderId)

    fun observeOperation(id: Long): Flow<OperationDetailRow?> = operations.observeDetail(id)

    fun searchOrders(query: String, status: OrderStatus?, from: Long, to: Long) =
        orders.searchHeaders(query.trim(), status, from, to)

    fun searchOperations(query: String, from: Long, to: Long) =
        operations.search(query.trim(), from, to)

    suspend fun combinationsOnce(orderId: Long): List<CombinationTotals> =
        items.totalsForOrder(orderId).map { it.toCombination() }

    // ---- order creation and editing ---------------------------------------------------

    suspend fun createOrder(draft: OrderDraft): Outcome<Long> {
        validateDraft(draft)?.let { return Outcome.Failure(it) }
        return outcomeCatching {
            db.withTransaction {
                val timestamp = now()
                val modelId = models.insert(
                    ModelEntity(
                        name = draft.modelName.trim(),
                        code = draft.modelCode?.trim()?.ifBlank { null },
                        imagePath = draft.imagePath,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                )
                val orderId = orders.insert(
                    CutOrderEntity(
                        modelId = modelId,
                        isDetailed = draft.isDetailed,
                        status = OrderStatus.OPEN,
                        notes = draft.orderNotes?.trim()?.ifBlank { null },
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                )
                draft.normalisedLines().forEach { line ->
                    items.insert(
                        OrderItemEntity(
                            orderId = orderId,
                            color = line.color,
                            size = line.size,
                            requiredQty = line.requiredQty,
                        ),
                    )
                }
                orderId
            }
        }
    }

    /**
     * Rewrites an order's model details and quantity lines.
     *
     * Lines are matched to existing rows by colour+size, so recorded cuts stay attached to
     * their combination. A line that has cuts against it cannot be dropped.
     */
    suspend fun updateOrder(orderId: Long, draft: OrderDraft): Outcome<Unit> {
        validateDraft(draft)?.let { return Outcome.Failure(it) }
        return outcomeCatching {
            db.withTransaction {
                val order = orders.byId(orderId) ?: return@withTransaction Outcome.Failure(AppError.NotFound)
                if (order.status == OrderStatus.CLOSED) {
                    return@withTransaction Outcome.Failure(AppError.OrderClosed)
                }
                val timestamp = now()
                val model = models.byId(order.modelId)
                    ?: return@withTransaction Outcome.Failure(AppError.NotFound)

                models.update(
                    model.copy(
                        name = draft.modelName.trim(),
                        code = draft.modelCode?.trim()?.ifBlank { null },
                        imagePath = draft.imagePath,
                        updatedAt = timestamp,
                    ),
                )

                val existing = items.byOrder(orderId).associateBy { it.color to it.size }
                val incoming = draft.normalisedLines()
                val incomingKeys = incoming.map { it.color to it.size }.toSet()

                for ((key, row) in existing) {
                    if (key !in incomingKeys) {
                        if (items.operationCountFor(row.id) > 0) {
                            return@withTransaction Outcome.Failure(AppError.ItemHasOperations)
                        }
                        items.deleteById(row.id)
                    }
                }
                for (line in incoming) {
                    val row = existing[line.color to line.size]
                    if (row == null) {
                        items.insert(
                            OrderItemEntity(
                                orderId = orderId,
                                color = line.color,
                                size = line.size,
                                requiredQty = line.requiredQty,
                            ),
                        )
                    } else if (row.requiredQty != line.requiredQty) {
                        items.update(row.copy(requiredQty = line.requiredQty))
                    }
                }

                orders.update(
                    order.copy(
                        isDetailed = draft.isDetailed,
                        notes = draft.orderNotes?.trim()?.ifBlank { null },
                        updatedAt = timestamp,
                    ),
                )
                Outcome.Success(Unit)
            }
        }.flatten()
    }

    /**
     * Adds to the required quantity of an open order (spec 7.1). Recorded cuts are untouched;
     * only the target figure moves.
     */
    suspend fun addQuantities(orderId: Long, lines: List<QuantityLine>): Outcome<Unit> =
        outcomeCatching {
            db.withTransaction {
                val order = orders.byId(orderId) ?: return@withTransaction Outcome.Failure(AppError.NotFound)
                if (order.status == OrderStatus.CLOSED) {
                    return@withTransaction Outcome.Failure(AppError.OrderClosed)
                }
                val positive = lines.filter { it.requiredQty > 0 }
                if (positive.isEmpty()) return@withTransaction Outcome.Failure(AppError.NoQuantities)

                val existing = items.byOrder(orderId).associateBy { it.color to it.size }
                for (line in positive) {
                    val color = if (order.isDetailed) line.color.trim() else ""
                    val size = if (order.isDetailed) line.size.trim() else ""
                    val row = existing[color to size]
                    if (row == null) {
                        items.insert(
                            OrderItemEntity(
                                orderId = orderId,
                                color = color,
                                size = size,
                                requiredQty = line.requiredQty,
                            ),
                        )
                    } else {
                        val combined = row.requiredQty.toLong() + line.requiredQty
                        if (combined > MAX_PIECES_PER_OPERATION) {
                            return@withTransaction Outcome.Failure(AppError.InvalidQuantity)
                        }
                        items.update(row.copy(requiredQty = combined.toInt()))
                    }
                }
                orders.update(order.copy(updatedAt = now()))
                Outcome.Success(Unit)
            }
        }.flatten()

    // ---- cut operations ----------------------------------------------------------------

    /**
     * Cut total for a combination with [excludeOperationId] left out, which is what the
     * overflow warning is measured against when an existing operation is being edited.
     */
    suspend fun cutTotalExcluding(itemId: Long, excludeOperationId: Long?): Int =
        operations.cutTotalForItem(itemId, excludeOperationId)

    suspend fun recordOperation(orderId: Long, draft: OperationDraft): Outcome<Long> =
        outcomeCatching {
            db.withTransaction {
                val order = orders.byId(orderId) ?: return@withTransaction Outcome.Failure(AppError.NotFound)
                if (order.status == OrderStatus.CLOSED) {
                    return@withTransaction Outcome.Failure(AppError.OrderClosed)
                }
                if (draft.pieces <= 0) return@withTransaction Outcome.Failure(AppError.InvalidQuantity)
                val item = items.byId(draft.orderItemId)
                if (item == null || item.orderId != orderId) {
                    return@withTransaction Outcome.Failure(AppError.NotFound)
                }
                val timestamp = now()
                val id = operations.insert(
                    CutOperationEntity(
                        orderId = orderId,
                        orderItemId = draft.orderItemId,
                        pieces = draft.pieces,
                        entryMethod = draft.entryMethod,
                        layers = draft.layers,
                        piecesPerLayer = draft.piecesPerLayer,
                        performedAt = draft.performedAt,
                        notes = draft.notes?.trim()?.ifBlank { null },
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                )
                orders.update(order.copy(updatedAt = timestamp))
                Outcome.Success(id)
            }
        }.flatten()

    /**
     * Edits an existing operation in place (spec 8). The row is updated, never duplicated, so
     * every total that reads from it moves with it.
     */
    suspend fun updateOperation(operationId: Long, draft: OperationDraft): Outcome<Unit> =
        outcomeCatching {
            db.withTransaction {
                val existing = operations.byId(operationId)
                    ?: return@withTransaction Outcome.Failure(AppError.NotFound)
                val order = orders.byId(existing.orderId)
                    ?: return@withTransaction Outcome.Failure(AppError.NotFound)
                if (order.status == OrderStatus.CLOSED) {
                    return@withTransaction Outcome.Failure(AppError.OrderClosed)
                }
                if (draft.pieces <= 0) return@withTransaction Outcome.Failure(AppError.InvalidQuantity)
                val item = items.byId(draft.orderItemId)
                if (item == null || item.orderId != existing.orderId) {
                    return@withTransaction Outcome.Failure(AppError.NotFound)
                }
                operations.update(
                    existing.copy(
                        orderItemId = draft.orderItemId,
                        pieces = draft.pieces,
                        entryMethod = draft.entryMethod,
                        layers = draft.layers,
                        piecesPerLayer = draft.piecesPerLayer,
                        performedAt = draft.performedAt,
                        notes = draft.notes?.trim()?.ifBlank { null },
                        updatedAt = now(),
                    ),
                )
                Outcome.Success(Unit)
            }
        }.flatten()

    // ---- status ------------------------------------------------------------------------

    suspend fun closeOrder(orderId: Long): Outcome<Unit> = outcomeCatching {
        db.withTransaction {
            val order = orders.byId(orderId) ?: return@withTransaction Outcome.Failure(AppError.NotFound)
            val timestamp = now()
            orders.update(
                order.copy(status = OrderStatus.CLOSED, closedAt = timestamp, updatedAt = timestamp),
            )
            Outcome.Success(Unit)
        }
    }.flatten()

    suspend fun reopenOrder(orderId: Long): Outcome<Unit> = outcomeCatching {
        db.withTransaction {
            val order = orders.byId(orderId) ?: return@withTransaction Outcome.Failure(AppError.NotFound)
            orders.update(
                order.copy(status = OrderStatus.OPEN, closedAt = null, updatedAt = now()),
            )
            Outcome.Success(Unit)
        }
    }.flatten()

    // ---- helpers -----------------------------------------------------------------------

    private fun validateDraft(draft: OrderDraft): AppError? {
        if (draft.modelName.isBlank()) return AppError.ModelNameRequired
        val lines = draft.normalisedLines()
        if (lines.isEmpty()) return AppError.NoQuantities
        if (lines.any { it.requiredQty < 0 }) return AppError.InvalidQuantity
        if (lines.sumOf { it.requiredQty.toLong() } <= 0L) return AppError.NoQuantities
        val keys = lines.map { it.color to it.size }
        if (keys.size != keys.toSet().size) return AppError.DuplicateCombination
        return null
    }

    /**
     * An order kept as a single total collapses to one blank colour+size row, so the rest of
     * the app has exactly one shape to work with.
     */
    private fun OrderDraft.normalisedLines(): List<QuantityLine> = if (isDetailed) {
        lines.map { QuantityLine(it.color.trim(), it.size.trim(), it.requiredQty) }
    } else {
        listOf(QuantityLine("", "", lines.sumOf { it.requiredQty }))
    }
}

private fun OrderItemTotalsRow.toCombination() = CombinationTotals(
    itemId = itemId,
    color = color,
    size = size,
    required = requiredQty,
    cut = cutQty,
)

private fun <T> Outcome<Outcome<T>>.flatten(): Outcome<T> = when (this) {
    is Outcome.Failure -> this
    is Outcome.Success -> value
}
