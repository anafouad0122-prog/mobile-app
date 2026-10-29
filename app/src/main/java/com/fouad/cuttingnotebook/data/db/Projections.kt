package com.fouad.cuttingnotebook.data.db

/** One colour+size row with its cut total already summed in SQL. */
data class OrderItemTotalsRow(
    val orderId: Long,
    val itemId: Long,
    val color: String,
    val size: String,
    val requiredQty: Int,
    val cutQty: Int,
)

/** An order with the model fields needed to render a card, without a second lookup. */
data class OrderHeaderRow(
    val orderId: Long,
    val modelId: Long,
    val modelName: String,
    val modelCode: String?,
    val imagePath: String?,
    val isDetailed: Boolean,
    val status: OrderStatus,
    val notes: String?,
    val createdAt: Long,
    val closedAt: Long?,
)

/** A cut operation together with enough context to show it in a history list. */
data class OperationDetailRow(
    val id: Long,
    val orderId: Long,
    val orderItemId: Long,
    val pieces: Int,
    val entryMethod: EntryMethod,
    val layers: Int?,
    val piecesPerLayer: Int?,
    val performedAt: Long,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val color: String,
    val size: String,
    val requiredQty: Int,
    val modelName: String,
    val modelCode: String?,
    val orderStatus: OrderStatus,
)

/** Report bucket: a period, or a model inside one. */
data class ReportBucketRow(
    val label: String,
    val pieces: Int,
    val operations: Int,
)
