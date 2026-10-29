package com.fouad.cuttingnotebook.domain

/**
 * Every failure the user can provoke, named rather than described, so the UI owns the Arabic
 * wording and the data layer owns the rule (spec 17).
 */
sealed interface AppError {
    /** A write was attempted against an order the operator has closed. */
    data object OrderClosed : AppError

    /** A colour+size row cannot be removed while cuts are recorded against it. */
    data object ItemHasOperations : AppError

    /** The order would be left with no quantities at all. */
    data object NoQuantities : AppError

    /** The same colour+size appears twice in one order. */
    data object DuplicateCombination : AppError

    data object InvalidQuantity : AppError

    data object ModelNameRequired : AppError

    data object NotFound : AppError

    /** Disk full, database locked, or anything else the storage layer threw. */
    data class Storage(val cause: Throwable) : AppError
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T> outcomeCatching(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    if (t is kotlinx.coroutines.CancellationException) throw t
    Outcome.Failure(AppError.Storage(t))
}
