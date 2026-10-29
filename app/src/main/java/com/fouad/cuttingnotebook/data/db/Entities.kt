package com.fouad.cuttingnotebook.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Orders are closed by hand, never automatically, so the status is stored rather than
 * derived from the quantities (spec 7.2).
 */
enum class OrderStatus { OPEN, CLOSED }

/** How the operator arrived at the piece count (spec 5.5). */
enum class EntryMethod { DIRECT, LAYERS }

@Entity(
    tableName = "models",
    indices = [Index("name"), Index("code")],
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Model name or code — whichever the operator writes on the order. Required. */
    val name: String,
    /** Optional separate code, when the factory uses one alongside the name. */
    val code: String? = null,
    /** Path inside the app's own files directory, so it survives the gallery changing. */
    val imagePath: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "cut_orders",
    foreignKeys = [
        ForeignKey(
            entity = ModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("modelId"), Index("status"), Index("createdAt")],
)
data class CutOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelId: Long,
    /**
     * True when quantities are broken down by colour and size. When false the order carries a
     * single total row whose colour and size are both blank (spec 5.3).
     */
    val isDetailed: Boolean,
    val status: OrderStatus,
    val notes: String? = null,
    val createdAt: Long,
    val closedAt: Long? = null,
    val updatedAt: Long,
)

/**
 * One required quantity per colour+size combination inside one order. Every cut operation
 * points at exactly one of these rows, which is what keeps the per-combination arithmetic
 * in spec 6 independent.
 */
@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = CutOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("orderId"),
        Index(value = ["orderId", "color", "size"], unique = true),
    ],
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    /** Blank on an order that was entered as a single total. */
    val color: String,
    /** Blank on an order that was entered as a single total. */
    val size: String,
    val requiredQty: Int,
)

/**
 * A recorded cut. [pieces] is always the authoritative figure; when the operator used the
 * layers method, [layers] and [piecesPerLayer] are kept alongside it as a record of how the
 * number was reached, and the product is what was stored in [pieces].
 */
@Entity(
    tableName = "cut_operations",
    foreignKeys = [
        ForeignKey(
            entity = CutOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OrderItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("orderId"), Index("orderItemId"), Index("performedAt")],
)
data class CutOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val orderItemId: Long,
    val pieces: Int,
    val entryMethod: EntryMethod,
    val layers: Int? = null,
    val piecesPerLayer: Int? = null,
    /** Device-local time the cut happened; reports are bucketed by this (spec 10). */
    val performedAt: Long,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
