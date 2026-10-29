package com.fouad.cuttingnotebook.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Insert
    suspend fun insert(model: ModelEntity): Long

    @Update
    suspend fun update(model: ModelEntity)

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun byId(id: Long): ModelEntity?

    @Query("SELECT * FROM models ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models")
    suspend fun all(): List<ModelEntity>

    @Query("SELECT imagePath FROM models WHERE imagePath IS NOT NULL")
    suspend fun allImagePaths(): List<String>
}

@Dao
interface OrderDao {
    @Insert
    suspend fun insert(order: CutOrderEntity): Long

    @Update
    suspend fun update(order: CutOrderEntity)

    @Query("SELECT * FROM cut_orders WHERE id = :id")
    suspend fun byId(id: Long): CutOrderEntity?

    @Query("SELECT * FROM cut_orders")
    suspend fun all(): List<CutOrderEntity>

    @Query(
        """
        SELECT o.id AS orderId, o.modelId AS modelId, m.name AS modelName, m.code AS modelCode,
               m.imagePath AS imagePath, o.isDetailed AS isDetailed, o.status AS status,
               o.notes AS notes, o.createdAt AS createdAt, o.closedAt AS closedAt
        FROM cut_orders o
        JOIN models m ON m.id = o.modelId
        WHERE o.status = :status
        ORDER BY o.createdAt DESC
        """,
    )
    fun observeHeadersByStatus(status: OrderStatus): Flow<List<OrderHeaderRow>>

    @Query(
        """
        SELECT o.id AS orderId, o.modelId AS modelId, m.name AS modelName, m.code AS modelCode,
               m.imagePath AS imagePath, o.isDetailed AS isDetailed, o.status AS status,
               o.notes AS notes, o.createdAt AS createdAt, o.closedAt AS closedAt
        FROM cut_orders o
        JOIN models m ON m.id = o.modelId
        WHERE o.id = :orderId
        """,
    )
    fun observeHeader(orderId: Long): Flow<OrderHeaderRow?>

    /**
     * Search across model name/code, and across the colours and sizes of the order's own rows,
     * optionally narrowed to a creation-date window. A blank query matches everything so the
     * date filter can stand on its own.
     */
    @Query(
        """
        SELECT DISTINCT o.id AS orderId, o.modelId AS modelId, m.name AS modelName,
               m.code AS modelCode, m.imagePath AS imagePath, o.isDetailed AS isDetailed,
               o.status AS status, o.notes AS notes, o.createdAt AS createdAt,
               o.closedAt AS closedAt
        FROM cut_orders o
        JOIN models m ON m.id = o.modelId
        LEFT JOIN order_items oi ON oi.orderId = o.id
        WHERE (:query = ''
               OR m.name LIKE '%' || :query || '%'
               OR IFNULL(m.code, '') LIKE '%' || :query || '%'
               OR oi.color LIKE '%' || :query || '%'
               OR oi.size LIKE '%' || :query || '%')
          AND (:statusFilter IS NULL OR o.status = :statusFilter)
          AND o.createdAt BETWEEN :from AND :to
        ORDER BY o.createdAt DESC
        """,
    )
    fun searchHeaders(
        query: String,
        statusFilter: OrderStatus?,
        from: Long,
        to: Long,
    ): Flow<List<OrderHeaderRow>>
}

@Dao
interface OrderItemDao {
    @Insert
    suspend fun insert(item: OrderItemEntity): Long

    @Update
    suspend fun update(item: OrderItemEntity)

    @Query("DELETE FROM order_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY color, size")
    suspend fun byOrder(orderId: Long): List<OrderItemEntity>

    @Query("SELECT * FROM order_items")
    suspend fun all(): List<OrderItemEntity>

    @Query("SELECT * FROM order_items WHERE id = :id")
    suspend fun byId(id: Long): OrderItemEntity?

    @Query("SELECT COUNT(*) FROM cut_operations WHERE orderItemId = :itemId")
    suspend fun operationCountFor(itemId: Long): Int

    /** Cut totals are summed in SQL; the clamping rules are applied in the domain layer. */
    @Query(
        """
        SELECT oi.orderId AS orderId, oi.id AS itemId, oi.color AS color, oi.size AS size,
               oi.requiredQty AS requiredQty,
               IFNULL((SELECT SUM(op.pieces) FROM cut_operations op WHERE op.orderItemId = oi.id), 0) AS cutQty
        FROM order_items oi
        WHERE oi.orderId = :orderId
        ORDER BY oi.color COLLATE NOCASE, oi.size COLLATE NOCASE
        """,
    )
    fun observeTotalsForOrder(orderId: Long): Flow<List<OrderItemTotalsRow>>

    @Query(
        """
        SELECT oi.orderId AS orderId, oi.id AS itemId, oi.color AS color, oi.size AS size,
               oi.requiredQty AS requiredQty,
               IFNULL((SELECT SUM(op.pieces) FROM cut_operations op WHERE op.orderItemId = oi.id), 0) AS cutQty
        FROM order_items oi
        ORDER BY oi.color COLLATE NOCASE, oi.size COLLATE NOCASE
        """,
    )
    fun observeAllTotals(): Flow<List<OrderItemTotalsRow>>

    @Query(
        """
        SELECT oi.orderId AS orderId, oi.id AS itemId, oi.color AS color, oi.size AS size,
               oi.requiredQty AS requiredQty,
               IFNULL((SELECT SUM(op.pieces) FROM cut_operations op WHERE op.orderItemId = oi.id), 0) AS cutQty
        FROM order_items oi
        WHERE oi.orderId = :orderId
        ORDER BY oi.color COLLATE NOCASE, oi.size COLLATE NOCASE
        """,
    )
    suspend fun totalsForOrder(orderId: Long): List<OrderItemTotalsRow>
}

@Dao
interface OperationDao {
    @Insert
    suspend fun insert(operation: CutOperationEntity): Long

    @Update
    suspend fun update(operation: CutOperationEntity)

    @Query("SELECT * FROM cut_operations WHERE id = :id")
    suspend fun byId(id: Long): CutOperationEntity?

    @Query("SELECT * FROM cut_operations")
    suspend fun all(): List<CutOperationEntity>

    @Query(
        """
        SELECT op.id AS id, op.orderId AS orderId, op.orderItemId AS orderItemId,
               op.pieces AS pieces, op.entryMethod AS entryMethod, op.layers AS layers,
               op.piecesPerLayer AS piecesPerLayer, op.performedAt AS performedAt,
               op.notes AS notes, op.createdAt AS createdAt, op.updatedAt AS updatedAt,
               oi.color AS color, oi.size AS size, oi.requiredQty AS requiredQty,
               m.name AS modelName, m.code AS modelCode, o.status AS orderStatus
        FROM cut_operations op
        JOIN order_items oi ON oi.id = op.orderItemId
        JOIN cut_orders o ON o.id = op.orderId
        JOIN models m ON m.id = o.modelId
        WHERE op.orderId = :orderId
        ORDER BY op.performedAt DESC, op.id DESC
        """,
    )
    fun observeForOrder(orderId: Long): Flow<List<OperationDetailRow>>

    @Query(
        """
        SELECT op.id AS id, op.orderId AS orderId, op.orderItemId AS orderItemId,
               op.pieces AS pieces, op.entryMethod AS entryMethod, op.layers AS layers,
               op.piecesPerLayer AS piecesPerLayer, op.performedAt AS performedAt,
               op.notes AS notes, op.createdAt AS createdAt, op.updatedAt AS updatedAt,
               oi.color AS color, oi.size AS size, oi.requiredQty AS requiredQty,
               m.name AS modelName, m.code AS modelCode, o.status AS orderStatus
        FROM cut_operations op
        JOIN order_items oi ON oi.id = op.orderItemId
        JOIN cut_orders o ON o.id = op.orderId
        JOIN models m ON m.id = o.modelId
        WHERE op.id = :id
        """,
    )
    fun observeDetail(id: Long): Flow<OperationDetailRow?>

    @Query(
        """
        SELECT op.id AS id, op.orderId AS orderId, op.orderItemId AS orderItemId,
               op.pieces AS pieces, op.entryMethod AS entryMethod, op.layers AS layers,
               op.piecesPerLayer AS piecesPerLayer, op.performedAt AS performedAt,
               op.notes AS notes, op.createdAt AS createdAt, op.updatedAt AS updatedAt,
               oi.color AS color, oi.size AS size, oi.requiredQty AS requiredQty,
               m.name AS modelName, m.code AS modelCode, o.status AS orderStatus
        FROM cut_operations op
        JOIN order_items oi ON oi.id = op.orderItemId
        JOIN cut_orders o ON o.id = op.orderId
        JOIN models m ON m.id = o.modelId
        WHERE (:query = ''
               OR m.name LIKE '%' || :query || '%'
               OR IFNULL(m.code, '') LIKE '%' || :query || '%'
               OR oi.color LIKE '%' || :query || '%'
               OR oi.size LIKE '%' || :query || '%')
          AND op.performedAt BETWEEN :from AND :to
        ORDER BY op.performedAt DESC, op.id DESC
        """,
    )
    fun search(query: String, from: Long, to: Long): Flow<List<OperationDetailRow>>

    /** Cut total for a combination, optionally ignoring one operation that is being edited. */
    @Query(
        """
        SELECT IFNULL(SUM(pieces), 0) FROM cut_operations
        WHERE orderItemId = :itemId AND (:excludeOperationId IS NULL OR id != :excludeOperationId)
        """,
    )
    suspend fun cutTotalForItem(itemId: Long, excludeOperationId: Long?): Int

    @Query("SELECT IFNULL(SUM(pieces), 0) FROM cut_operations WHERE performedAt BETWEEN :from AND :to")
    fun observePiecesInRange(from: Long, to: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cut_operations WHERE performedAt BETWEEN :from AND :to")
    fun observeOperationCountInRange(from: Long, to: Long): Flow<Int>

    @Query(
        """
        SELECT (CASE WHEN IFNULL(m.code, '') = '' THEN m.name ELSE m.name || ' · ' || m.code END) AS label,
               SUM(op.pieces) AS pieces,
               COUNT(*) AS operations
        FROM cut_operations op
        JOIN cut_orders o ON o.id = op.orderId
        JOIN models m ON m.id = o.modelId
        WHERE op.performedAt BETWEEN :from AND :to
        GROUP BY m.id
        ORDER BY pieces DESC
        """,
    )
    fun observePiecesByModel(from: Long, to: Long): Flow<List<ReportBucketRow>>

    /**
     * Daily buckets in the device's local calendar. [offsetSeconds] shifts epoch seconds into
     * local time before the date is taken, so a cut at 1am counts for that local day.
     */
    @Query(
        """
        SELECT date((op.performedAt / 1000) + :offsetSeconds, 'unixepoch') AS label,
               SUM(op.pieces) AS pieces,
               COUNT(*) AS operations
        FROM cut_operations op
        WHERE op.performedAt BETWEEN :from AND :to
        GROUP BY label
        ORDER BY label DESC
        """,
    )
    fun observePiecesByDay(from: Long, to: Long, offsetSeconds: Long): Flow<List<ReportBucketRow>>

    @Query("SELECT MIN(performedAt) FROM cut_operations")
    fun observeEarliestOperation(): Flow<Long?>
}
