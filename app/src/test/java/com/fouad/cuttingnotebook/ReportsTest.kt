package com.fouad.cuttingnotebook

import com.fouad.cuttingnotebook.data.db.CuttingDatabase
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.domain.ReportPeriod
import com.fouad.cuttingnotebook.domain.localOffsetSeconds
import com.fouad.cuttingnotebook.domain.reportRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Acceptance test 9: cuts land in the right daily, weekly and monthly periods. */
@RunWith(RobolectricTestRunner::class)
class ReportsTest {

    private lateinit var db: CuttingDatabase
    private lateinit var repo: OrderRepository

    private val zone: ZoneId = ZoneId.of("Africa/Cairo")

    // Wednesday 11 March 2026, inside the Saturday-to-Friday week starting 7 March.
    private val inWeek = LocalDate.of(2026, 3, 11)
    private val sameWeekEarlier = LocalDate.of(2026, 3, 9)
    private val previousWeekSameMonth = LocalDate.of(2026, 3, 3)
    private val previousMonth = LocalDate.of(2026, 2, 20)

    @Before
    fun setUp() {
        db = TestFixtures.inMemoryDatabase()
        repo = TestFixtures.repository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 1000)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()
        listOf(
            inWeek to 10,
            sameWeekEarlier to 7,
            previousWeekSameMonth to 5,
            previousMonth to 3,
        ).forEach { (date, pieces) ->
            repo.recordOperation(
                orderId,
                TestFixtures.directCut(item.itemId, pieces, TestFixtures.millisAt(date, zone)),
            ).expectSuccess()
        }
    }

    private suspend fun piecesIn(period: ReportPeriod, anchor: LocalDate): Int {
        val range = reportRange(period, anchor, zone, earliestOperationMillis = null)
        return db.operationDao().observePiecesInRange(range.fromMillis, range.toMillis).first()
    }

    @Test
    fun `the daily report counts only that day`() = runTest {
        seed()
        assertEquals(10, piecesIn(ReportPeriod.DAY, inWeek))
        assertEquals(7, piecesIn(ReportPeriod.DAY, sameWeekEarlier))
        assertEquals(0, piecesIn(ReportPeriod.DAY, LocalDate.of(2026, 3, 12)))
    }

    @Test
    fun `the weekly report covers Saturday to Friday`() = runTest {
        seed()
        // 9 and 11 March fall in the week beginning Saturday 7 March; 3 March does not.
        assertEquals(17, piecesIn(ReportPeriod.WEEK, inWeek))
        assertEquals(5, piecesIn(ReportPeriod.WEEK, previousWeekSameMonth))
    }

    @Test
    fun `the monthly report covers the calendar month`() = runTest {
        seed()
        assertEquals(22, piecesIn(ReportPeriod.MONTH, inWeek))
        assertEquals(3, piecesIn(ReportPeriod.MONTH, previousMonth))
    }

    @Test
    fun `the all-time report counts every recorded cut`() = runTest {
        seed()
        val earliest = db.operationDao().observeEarliestOperation().first()
        val range = reportRange(ReportPeriod.ALL, LocalDate.of(2026, 3, 31), zone, earliest)
        val total = db.operationDao()
            .observePiecesInRange(range.fromMillis, range.toMillis)
            .first()

        assertEquals(25, total)
    }

    @Test
    fun `editing an old operation moves its pieces in the reports`() = runTest {
        seed()
        val operation = db.operationDao().all().first { it.performedAt == TestFixtures.millisAt(inWeek, zone) }

        repo.updateOperation(
            operation.id,
            TestFixtures.directCut(operation.orderItemId, 4, TestFixtures.millisAt(inWeek, zone)),
        ).expectSuccess()

        assertEquals(4, piecesIn(ReportPeriod.DAY, inWeek))
        assertEquals(11, piecesIn(ReportPeriod.WEEK, inWeek))
        assertEquals(16, piecesIn(ReportPeriod.MONTH, inWeek))
    }

    @Test
    fun `daily buckets use the device calendar rather than UTC`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 100)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()

        // 00:30 local on 11 March is still 10 March in UTC; it must count for the 11th.
        val lateNight = inWeek.atTime(0, 30).atZone(zone).toInstant().toEpochMilli()
        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 6, lateNight),
        ).expectSuccess()

        val range = reportRange(ReportPeriod.DAY, inWeek, zone, null)
        val buckets = db.operationDao()
            .observePiecesByDay(
                range.fromMillis,
                range.toMillis,
                localOffsetSeconds(zone, range.fromMillis),
            )
            .first()

        assertEquals(1, buckets.size)
        assertEquals("2026-03-11", buckets.single().label)
        assertEquals(6, buckets.single().pieces)
    }

    @Test
    fun `a report groups pieces by model`() = runTest {
        val first = repo.createOrder(TestFixtures.totalOrder("M-101", 100)).expectSuccess()
        val second = repo.createOrder(TestFixtures.totalOrder("M-202", 100)).expectSuccess()
        repo.recordOperation(
            first,
            TestFixtures.directCut(
                repo.combinationsOnce(first).single().itemId,
                12,
                TestFixtures.millisAt(inWeek, zone),
            ),
        ).expectSuccess()
        repo.recordOperation(
            second,
            TestFixtures.directCut(
                repo.combinationsOnce(second).single().itemId,
                30,
                TestFixtures.millisAt(inWeek, zone),
            ),
        ).expectSuccess()

        val range = reportRange(ReportPeriod.DAY, inWeek, zone, null)
        val buckets = db.operationDao()
            .observePiecesByModel(range.fromMillis, range.toMillis)
            .first()

        assertEquals(listOf("M-202", "M-101"), buckets.map { it.label })
        assertEquals(listOf(30, 12), buckets.map { it.pieces })
    }
}
