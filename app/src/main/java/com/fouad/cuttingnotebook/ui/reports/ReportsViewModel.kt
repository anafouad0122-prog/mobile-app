package com.fouad.cuttingnotebook.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fouad.cuttingnotebook.data.db.OperationDao
import com.fouad.cuttingnotebook.data.db.ReportBucketRow
import com.fouad.cuttingnotebook.domain.DateRange
import com.fouad.cuttingnotebook.domain.ReportPeriod
import com.fouad.cuttingnotebook.domain.localOffsetSeconds
import com.fouad.cuttingnotebook.domain.reportRange
import com.fouad.cuttingnotebook.domain.shiftAnchor
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ReportsState(
    val period: ReportPeriod = ReportPeriod.DAY,
    val anchor: LocalDate = LocalDate.now(),
    val range: DateRange? = null,
    val pieces: Int = 0,
    val operations: Int = 0,
    val byModel: List<ReportBucketRow> = emptyList(),
    val byDay: List<ReportBucketRow> = emptyList(),
)

data class ReportSelection(val period: ReportPeriod, val anchor: LocalDate)

/**
 * Production reports (spec 10).
 *
 * Everything is counted from recorded cut operations and bucketed by the device's own
 * calendar, so editing an old operation moves its pieces into the right period without
 * anything needing to be recomputed by hand.
 */
class ReportsViewModel(
    private val operationDao: OperationDao,
    private val zone: ZoneId,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _selection = MutableStateFlow(ReportSelection(ReportPeriod.DAY, today()))
    val selection: StateFlow<ReportSelection> = _selection.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportsState> = combine(
        _selection,
        operationDao.observeEarliestOperation(),
    ) { selection, earliest -> selection to earliest }
        .flatMapLatest { (selection, earliest) ->
            val range = reportRange(selection.period, selection.anchor, zone, earliest)
            val offset = localOffsetSeconds(zone, range.fromMillis)
            combine(
                operationDao.observePiecesInRange(range.fromMillis, range.toMillis),
                operationDao.observeOperationCountInRange(range.fromMillis, range.toMillis),
                operationDao.observePiecesByModel(range.fromMillis, range.toMillis),
                operationDao.observePiecesByDay(range.fromMillis, range.toMillis, offset),
            ) { pieces, count, byModel, byDay ->
                ReportsState(
                    period = selection.period,
                    anchor = selection.anchor,
                    range = range,
                    pieces = pieces,
                    operations = count,
                    byModel = byModel,
                    byDay = byDay,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState())

    fun setPeriod(period: ReportPeriod) =
        _selection.update { ReportSelection(period, today()) }

    fun step(steps: Long) = _selection.update {
        ReportSelection(it.period, shiftAnchor(it.period, it.anchor, steps))
    }

    /** Stepping forward past today would show an empty future period. */
    fun canStepForward(): Boolean {
        val selection = _selection.value
        if (selection.period == ReportPeriod.ALL) return false
        return shiftAnchor(selection.period, selection.anchor, 1).isBefore(today().plusDays(1))
    }
}
