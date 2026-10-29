package com.fouad.cuttingnotebook.domain

/**
 * The quantity rules from spec section 6, in one place.
 *
 * Every total in the app — order cards, order details, the close dialog, reports — is built
 * from these functions over recorded cut operations. Nothing stores a running total of its
 * own, which is what keeps an edited operation from being counted twice.
 */

/** Totals for one colour+size combination. */
data class CombinationTotals(
    val itemId: Long,
    val color: String,
    val size: String,
    val required: Int,
    /** Sum of every operation recorded against this combination. */
    val cut: Int,
) {
    /** Never below zero (spec 6). */
    val remaining: Int get() = (required - cut).coerceAtLeast(0)

    /** How far the cut count runs past what was asked for. */
    val excess: Int get() = (cut - required).coerceAtLeast(0)

    val isComplete: Boolean get() = cut >= required
}

/**
 * Order-level totals.
 *
 * Remaining and excess are summed per combination, not derived from the order's own
 * required-minus-cut: a shortfall on one colour must not be cancelled out by an overrun
 * on another.
 */
data class OrderTotals(
    val required: Int,
    val cut: Int,
    val remaining: Int,
    val excess: Int,
) {
    val isComplete: Boolean get() = remaining == 0 && required > 0

    companion object {
        val EMPTY = OrderTotals(0, 0, 0, 0)
    }
}

fun List<CombinationTotals>.orderTotals(): OrderTotals = OrderTotals(
    required = sumOf { it.required },
    cut = sumOf { it.cut },
    remaining = sumOf { it.remaining },
    excess = sumOf { it.excess },
)

/**
 * What saving [newPieces] against a combination would do to it.
 *
 * [currentCutExcludingThisOperation] is the combination's cut total with the operation being
 * edited left out, so editing an existing operation is checked against the same rule as
 * recording a new one.
 */
data class OverflowCheck(
    val required: Int,
    val cutAfter: Int,
    val excessAfter: Int,
) {
    val overflows: Boolean get() = excessAfter > 0
}

fun checkOverflow(
    required: Int,
    currentCutExcludingThisOperation: Int,
    newPieces: Int,
): OverflowCheck {
    val cutAfter = currentCutExcludingThisOperation + newPieces
    return OverflowCheck(
        required = required,
        cutAfter = cutAfter,
        excessAfter = (cutAfter - required).coerceAtLeast(0),
    )
}

/** Result of validating what the operator typed into the cut screen. */
sealed interface PieceCountResult {
    data class Valid(val pieces: Int) : PieceCountResult
    data class Invalid(val reason: PieceCountError) : PieceCountResult
}

enum class PieceCountError {
    EMPTY,
    NOT_A_NUMBER,
    NOT_POSITIVE,
    TOO_LARGE,
}

/** Guards against a single entry so large it would swamp the running totals. */
const val MAX_PIECES_PER_OPERATION = 1_000_000

fun parseDirectCount(raw: String): PieceCountResult {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return PieceCountResult.Invalid(PieceCountError.EMPTY)
    val value = trimmed.toIntOrNull() ?: return PieceCountResult.Invalid(PieceCountError.NOT_A_NUMBER)
    if (value <= 0) return PieceCountResult.Invalid(PieceCountError.NOT_POSITIVE)
    if (value > MAX_PIECES_PER_OPERATION) return PieceCountResult.Invalid(PieceCountError.TOO_LARGE)
    return PieceCountResult.Valid(value)
}

/**
 * Layers method: layers x pieces-per-layer, shown to the operator before saving (spec 5.5).
 */
fun parseLayerCount(rawLayers: String, rawPerLayer: String): PieceCountResult {
    val layers = when (val r = parseDirectCount(rawLayers)) {
        is PieceCountResult.Invalid -> return r
        is PieceCountResult.Valid -> r.pieces
    }
    val perLayer = when (val r = parseDirectCount(rawPerLayer)) {
        is PieceCountResult.Invalid -> return r
        is PieceCountResult.Valid -> r.pieces
    }
    val product = layers.toLong() * perLayer.toLong()
    if (product > MAX_PIECES_PER_OPERATION) return PieceCountResult.Invalid(PieceCountError.TOO_LARGE)
    return PieceCountResult.Valid(product.toInt())
}

/** Required quantities may be zero (a combination listed but not yet ordered), never negative. */
fun parseRequiredQty(raw: String): PieceCountResult {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return PieceCountResult.Invalid(PieceCountError.EMPTY)
    val value = trimmed.toIntOrNull() ?: return PieceCountResult.Invalid(PieceCountError.NOT_A_NUMBER)
    if (value < 0) return PieceCountResult.Invalid(PieceCountError.NOT_POSITIVE)
    if (value > MAX_PIECES_PER_OPERATION) return PieceCountResult.Invalid(PieceCountError.TOO_LARGE)
    return PieceCountResult.Valid(value)
}
