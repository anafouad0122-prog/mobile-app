package com.fouad.cuttingnotebook.ui.model

import com.fouad.cuttingnotebook.data.db.OrderHeaderRow
import com.fouad.cuttingnotebook.data.db.OrderStatus
import com.fouad.cuttingnotebook.domain.CombinationTotals
import com.fouad.cuttingnotebook.domain.OrderTotals
import com.fouad.cuttingnotebook.domain.orderTotals

/** One order as the list screens render it. */
data class OrderCardUi(
    val orderId: Long,
    val modelName: String,
    val modelCode: String?,
    val imagePath: String?,
    val isDetailed: Boolean,
    val status: OrderStatus,
    val createdAt: Long,
    val closedAt: Long?,
    val colors: List<String>,
    val sizes: List<String>,
    val totals: OrderTotals,
)

fun OrderHeaderRow.toCard(combinations: List<CombinationTotals>) = OrderCardUi(
    orderId = orderId,
    modelName = modelName,
    modelCode = modelCode,
    imagePath = imagePath,
    isDetailed = isDetailed,
    status = status,
    createdAt = createdAt,
    closedAt = closedAt,
    colors = combinations.map { it.color }.filter { it.isNotBlank() }.distinct(),
    sizes = combinations.map { it.size }.filter { it.isNotBlank() }.distinct(),
    totals = combinations.orderTotals(),
)

fun List<OrderHeaderRow>.toCards(byOrder: Map<Long, List<CombinationTotals>>): List<OrderCardUi> =
    map { it.toCard(byOrder[it.orderId].orEmpty()) }

/** Order totals reduced to the three numbers shown on a card. */
fun OrderTotals.summaryTriple(): Triple<Int, Int, Int> = Triple(required, cut, remaining)
