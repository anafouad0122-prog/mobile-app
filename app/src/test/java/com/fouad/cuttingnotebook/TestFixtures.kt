package com.fouad.cuttingnotebook

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fouad.cuttingnotebook.data.db.CuttingDatabase
import com.fouad.cuttingnotebook.data.db.EntryMethod
import com.fouad.cuttingnotebook.data.repo.OperationDraft
import com.fouad.cuttingnotebook.data.repo.OrderDraft
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.data.repo.QuantityLine
import com.fouad.cuttingnotebook.domain.Outcome
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Shared setup for the acceptance tests in this package. */
object TestFixtures {

    fun inMemoryDatabase(): CuttingDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, CuttingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    fun repository(db: CuttingDatabase, now: () -> Long = System::currentTimeMillis) =
        OrderRepository(db, now)

    /** Millis for a local date at a fixed mid-morning time, so tests do not straddle midnight. */
    fun millisAt(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant().toEpochMilli()

    fun detailedOrder(
        modelName: String = "M-101",
        lines: List<QuantityLine>,
    ) = OrderDraft(
        modelName = modelName,
        modelCode = null,
        imagePath = null,
        orderNotes = null,
        isDetailed = true,
        lines = lines,
    )

    fun totalOrder(modelName: String = "M-101", quantity: Int) = OrderDraft(
        modelName = modelName,
        modelCode = null,
        imagePath = null,
        orderNotes = null,
        isDetailed = false,
        lines = listOf(QuantityLine("", "", quantity)),
    )

    fun directCut(itemId: Long, pieces: Int, at: Long) = OperationDraft(
        orderItemId = itemId,
        pieces = pieces,
        entryMethod = EntryMethod.DIRECT,
        layers = null,
        piecesPerLayer = null,
        performedAt = at,
        notes = null,
    )

    fun layerCut(itemId: Long, layers: Int, perLayer: Int, at: Long) = OperationDraft(
        orderItemId = itemId,
        pieces = layers * perLayer,
        entryMethod = EntryMethod.LAYERS,
        layers = layers,
        piecesPerLayer = perLayer,
        performedAt = at,
        notes = null,
    )
}

/** Unwraps a successful outcome, failing the test with the error if there is one. */
fun <T> Outcome<T>.expectSuccess(): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> throw AssertionError("expected success but got $error")
}

fun <T> Outcome<T>.expectFailure() = when (this) {
    is Outcome.Failure -> error
    is Outcome.Success -> throw AssertionError("expected failure but got $value")
}
