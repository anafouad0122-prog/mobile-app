package com.fouad.cuttingnotebook

import com.fouad.cuttingnotebook.domain.CombinationTotals
import com.fouad.cuttingnotebook.domain.PieceCountError
import com.fouad.cuttingnotebook.domain.PieceCountResult
import com.fouad.cuttingnotebook.domain.checkOverflow
import com.fouad.cuttingnotebook.domain.orderTotals
import com.fouad.cuttingnotebook.domain.parseDirectCount
import com.fouad.cuttingnotebook.domain.parseLayerCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arithmetic from spec section 6, tested without a database in the way. */
class QuantityRulesTest {

    @Test
    fun `remaining never goes below zero and excess is reported separately`() {
        val combination = CombinationTotals(1, "أحمر", "4", required = 30, cut = 35)

        assertEquals(0, combination.remaining)
        assertEquals(5, combination.excess)
        assertTrue(combination.isComplete)
    }

    @Test
    fun `spec example - 30 required, 20 cut leaves 10`() {
        val combination = CombinationTotals(1, "أحمر", "4", required = 30, cut = 20)

        assertEquals(10, combination.remaining)
        assertEquals(0, combination.excess)
        assertFalse(combination.isComplete)
    }

    @Test
    fun `a shortfall on one colour is not cancelled by an overrun on another`() {
        val totals = listOf(
            CombinationTotals(1, "أحمر", "4", required = 10, cut = 20),
            CombinationTotals(2, "أزرق", "4", required = 10, cut = 0),
        ).orderTotals()

        assertEquals(20, totals.required)
        assertEquals(20, totals.cut)
        // Naive required-minus-cut would read zero here, hiding the blue that was never cut.
        assertEquals(10, totals.remaining)
        assertEquals(10, totals.excess)
        assertFalse(totals.isComplete)
    }

    @Test
    fun `acceptance test 4 - layers method multiplies before saving`() {
        val result = parseLayerCount("10", "2")

        assertEquals(PieceCountResult.Valid(20), result)
    }

    @Test
    fun `acceptance test 5 - overflow is detected with the excess it would create`() {
        // 20 required, 15 already cut, operator enters 10 more.
        val check = checkOverflow(required = 20, currentCutExcludingThisOperation = 15, newPieces = 10)

        assertTrue(check.overflows)
        assertEquals(25, check.cutAfter)
        assertEquals(5, check.excessAfter)
    }

    @Test
    fun `an entry that exactly meets the requirement does not warn`() {
        val check = checkOverflow(required = 20, currentCutExcludingThisOperation = 15, newPieces = 5)

        assertFalse(check.overflows)
        assertEquals(0, check.excessAfter)
    }

    @Test
    fun `invalid piece counts are rejected with a reason`() {
        assertEquals(
            PieceCountResult.Invalid(PieceCountError.EMPTY),
            parseDirectCount("  "),
        )
        assertEquals(
            PieceCountResult.Invalid(PieceCountError.NOT_POSITIVE),
            parseDirectCount("0"),
        )
        assertEquals(
            PieceCountResult.Invalid(PieceCountError.NOT_A_NUMBER),
            parseDirectCount("abc"),
        )
        assertEquals(
            PieceCountResult.Invalid(PieceCountError.TOO_LARGE),
            parseLayerCount("100000", "100000"),
        )
    }
}
