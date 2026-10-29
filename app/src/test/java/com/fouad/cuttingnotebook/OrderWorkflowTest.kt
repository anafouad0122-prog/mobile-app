package com.fouad.cuttingnotebook

import com.fouad.cuttingnotebook.data.db.CuttingDatabase
import com.fouad.cuttingnotebook.data.db.OrderStatus
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.data.repo.QuantityLine
import com.fouad.cuttingnotebook.domain.AppError
import com.fouad.cuttingnotebook.domain.orderTotals
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The order-and-cut acceptance tests from spec section 18, run against a real Room database.
 */
@RunWith(RobolectricTestRunner::class)
class OrderWorkflowTest {

    private lateinit var db: CuttingDatabase
    private lateinit var repo: OrderRepository

    private val day1 = LocalDate.of(2026, 3, 10)
    private val day2 = LocalDate.of(2026, 3, 11)

    @Before
    fun setUp() {
        db = TestFixtures.inMemoryDatabase()
        repo = TestFixtures.repository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `test 1 - an order with two colours and two sizes totals correctly`() = runTest {
        val orderId = repo.createOrder(
            TestFixtures.detailedOrder(
                lines = listOf(
                    QuantityLine("أحمر", "4", 10),
                    QuantityLine("أحمر", "6", 15),
                    QuantityLine("أزرق", "4", 5),
                    QuantityLine("أزرق", "6", 10),
                ),
            ),
        ).expectSuccess()

        val combinations = repo.combinationsOnce(orderId)

        assertEquals(4, combinations.size)
        assertEquals(40, combinations.orderTotals().required)
        assertEquals(0, combinations.orderTotals().cut)
        assertEquals(40, combinations.orderTotals().remaining)
        // Each combination keeps its own figure rather than sharing a pooled one.
        assertEquals(
            listOf(10, 15, 5, 10).sorted(),
            combinations.map { it.required }.sorted(),
        )
    }

    @Test
    fun `test 2 - a partial cut of 10 against 30 leaves 20`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 30)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()

        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 10, TestFixtures.millisAt(day1)),
        ).expectSuccess()

        val after = repo.combinationsOnce(orderId).single()
        assertEquals(30, after.required)
        assertEquals(10, after.cut)
        assertEquals(20, after.remaining)
    }

    @Test
    fun `test 3 - cuts on two days accumulate and keep their own dates`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 30)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()

        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 10, TestFixtures.millisAt(day1)),
        ).expectSuccess()
        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 5, TestFixtures.millisAt(day2)),
        ).expectSuccess()

        val after = repo.combinationsOnce(orderId).single()
        assertEquals(15, after.cut)
        assertEquals(15, after.remaining)

        val operations = repo.observeOperations(orderId).first()
        assertEquals(2, operations.size)
        assertEquals(
            listOf(TestFixtures.millisAt(day1), TestFixtures.millisAt(day2)).sorted(),
            operations.map { it.performedAt }.sorted(),
        )
    }

    @Test
    fun `test 4 - a layers entry stores the product and how it was reached`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 50)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()

        repo.recordOperation(
            orderId,
            TestFixtures.layerCut(item.itemId, layers = 10, perLayer = 2, at = TestFixtures.millisAt(day1)),
        ).expectSuccess()

        val operation = repo.observeOperations(orderId).first().single()
        assertEquals(20, operation.pieces)
        assertEquals(10, operation.layers)
        assertEquals(2, operation.piecesPerLayer)
        assertEquals(20, repo.combinationsOnce(orderId).single().cut)
    }

    @Test
    fun `test 5 - an overrun is visible in the totals rather than clipped away`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 20)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()

        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 15, TestFixtures.millisAt(day1)),
        ).expectSuccess()

        // What the warning dialog is built from, before the confirmed save.
        val baseline = repo.cutTotalExcluding(item.itemId, null)
        assertEquals(15, baseline)

        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 10, TestFixtures.millisAt(day1)),
        ).expectSuccess()

        val after = repo.combinationsOnce(orderId).single()
        assertEquals(25, after.cut)
        assertEquals(0, after.remaining)
        assertEquals(5, after.excess)
    }

    @Test
    fun `test 6 - editing an operation moves the totals instead of adding a second one`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 30)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()

        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 10, TestFixtures.millisAt(day1)),
        ).expectSuccess()
        val operation = repo.observeOperations(orderId).first().single()

        repo.updateOperation(
            operation.id,
            TestFixtures.directCut(item.itemId, 8, TestFixtures.millisAt(day1)),
        ).expectSuccess()

        val operations = repo.observeOperations(orderId).first()
        assertEquals(1, operations.size)
        assertEquals(8, operations.single().pieces)

        val after = repo.combinationsOnce(orderId).single()
        assertEquals(8, after.cut)
        assertEquals(22, after.remaining)
    }

    @Test
    fun `editing an operation is measured against the totals without its own old figure`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 20)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()
        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 20, TestFixtures.millisAt(day1)),
        ).expectSuccess()
        val operation = repo.observeOperations(orderId).first().single()

        // Correcting 20 down to 18 must not look like an overrun.
        val baseline = repo.cutTotalExcluding(item.itemId, operation.id)
        assertEquals(0, baseline)
    }

    @Test
    fun `test 7 - closing moves an order out of the open list and reopening brings it back`() =
        runTest {
            val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 10)).expectSuccess()

            repo.closeOrder(orderId).expectSuccess()

            assertTrue(repo.observeOpenOrders().first().none { it.orderId == orderId })
            val closed = repo.observeClosedOrders().first().single { it.orderId == orderId }
            assertEquals(OrderStatus.CLOSED, closed.status)
            assertTrue(closed.closedAt != null)

            repo.reopenOrder(orderId).expectSuccess()

            val reopened = repo.observeOpenOrders().first().single { it.orderId == orderId }
            assertEquals(OrderStatus.OPEN, reopened.status)
            assertNull(reopened.closedAt)
        }

    @Test
    fun `a closed order rejects new cuts until it is reopened`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 10)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()
        repo.closeOrder(orderId).expectSuccess()

        val error = repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 5, TestFixtures.millisAt(day1)),
        ).expectFailure()
        assertEquals(AppError.OrderClosed, error)

        repo.reopenOrder(orderId).expectSuccess()
        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 5, TestFixtures.millisAt(day1)),
        ).expectSuccess()
    }

    @Test
    fun `closing does not erase the operations recorded against the order`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 10)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()
        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 4, TestFixtures.millisAt(day1)),
        ).expectSuccess()

        repo.closeOrder(orderId).expectSuccess()

        assertEquals(1, repo.observeOperations(orderId).first().size)
        assertEquals(4, repo.combinationsOnce(orderId).single().cut)
    }

    @Test
    fun `test 8 - two orders for the same model keep their quantities apart`() = runTest {
        val first = repo.createOrder(TestFixtures.totalOrder("M-101", 30)).expectSuccess()
        val second = repo.createOrder(TestFixtures.totalOrder("M-101", 50)).expectSuccess()

        val firstItem = repo.combinationsOnce(first).single()
        val secondItem = repo.combinationsOnce(second).single()

        repo.recordOperation(
            first,
            TestFixtures.directCut(firstItem.itemId, 30, TestFixtures.millisAt(day1)),
        ).expectSuccess()

        assertEquals(30, repo.combinationsOnce(first).single().cut)
        assertEquals(0, repo.combinationsOnce(second).single().cut)
        assertEquals(50, repo.combinationsOnce(second).single().remaining)
        assertEquals(1, repo.observeOperations(first).first().size)
        assertEquals(0, repo.observeOperations(second).first().size)
    }

    @Test
    fun `adding quantity raises the requirement without touching recorded cuts`() = runTest {
        val orderId = repo.createOrder(TestFixtures.totalOrder(quantity = 30)).expectSuccess()
        val item = repo.combinationsOnce(orderId).single()
        repo.recordOperation(
            orderId,
            TestFixtures.directCut(item.itemId, 30, TestFixtures.millisAt(day1)),
        ).expectSuccess()

        repo.addQuantities(orderId, listOf(QuantityLine("", "", 20))).expectSuccess()

        val after = repo.combinationsOnce(orderId).single()
        assertEquals(50, after.required)
        assertEquals(30, after.cut)
        assertEquals(20, after.remaining)
        assertEquals(1, repo.observeOperations(orderId).first().size)
    }

    @Test
    fun `a quantity row with cuts against it cannot be dropped by an edit`() = runTest {
        val orderId = repo.createOrder(
            TestFixtures.detailedOrder(
                lines = listOf(QuantityLine("أحمر", "4", 10), QuantityLine("أزرق", "4", 10)),
            ),
        ).expectSuccess()
        val red = repo.combinationsOnce(orderId).first { it.color == "أحمر" }
        repo.recordOperation(
            orderId,
            TestFixtures.directCut(red.itemId, 5, TestFixtures.millisAt(day1)),
        ).expectSuccess()

        val error = repo.updateOrder(
            orderId,
            TestFixtures.detailedOrder(lines = listOf(QuantityLine("أزرق", "4", 10))),
        ).expectFailure()

        assertEquals(AppError.ItemHasOperations, error)
        // Nothing was changed by the rejected edit.
        assertEquals(2, repo.combinationsOnce(orderId).size)
        assertEquals(5, repo.combinationsOnce(orderId).first { it.color == "أحمر" }.cut)
    }

    @Test
    fun `an order cannot be saved without a model name or a quantity`() = runTest {
        assertEquals(
            AppError.ModelNameRequired,
            repo.createOrder(TestFixtures.totalOrder("   ", 10)).expectFailure(),
        )
        assertEquals(
            AppError.NoQuantities,
            repo.createOrder(TestFixtures.totalOrder("M-9", 0)).expectFailure(),
        )
    }
}
