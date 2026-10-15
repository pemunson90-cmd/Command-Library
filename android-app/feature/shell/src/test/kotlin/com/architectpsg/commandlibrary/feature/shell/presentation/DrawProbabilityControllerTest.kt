package com.architectpsg.commandlibrary.feature.shell.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DrawProbabilityControllerTest {
    @Test fun exactRealCalculationRetainsReadOnlyResultAcrossHideAndResume() = runTest {
        val controller = DrawProbabilityController(this, StandardTestDispatcher(testScheduler))
        controller.open(4); controller.successes("2"); controller.draws("2"); controller.calculate(); runCurrent()
        val exact = checkNotNull(controller.state.value.result)
        assertEquals(BigInteger.valueOf(6), exact.totalWays)
        assertEquals(listOf(1L, 4L, 1L), exact.points.map { it.ways.longValueExact() })
        controller.hide(); assertFalse(controller.state.value.visible)
        controller.open(99); assertTrue(controller.state.value.visible)
        assertEquals("4", controller.state.value.population); assertSame(exact, controller.state.value.result)
    }
    @Test fun editsAndHideFenceQueuedWorkWithoutPublishingOldOrHiddenResults() = runTest {
        val controller = DrawProbabilityController(this, StandardTestDispatcher(testScheduler))
        controller.open(60); controller.successes("24"); controller.calculate()
        assertTrue(controller.state.value.busy)
        controller.successes("25"); runCurrent()
        assertFalse(controller.state.value.busy); assertNull(controller.state.value.result)
        controller.calculate(); controller.hide(); runCurrent()
        assertFalse(controller.state.value.visible); assertFalse(controller.state.value.busy); assertNull(controller.state.value.result)
        controller.open(99); controller.calculate(); runCurrent()
        assertEquals(25, checkNotNull(controller.state.value.result).successes)
    }
    @Test fun impossibleOrOverflowedInputIsNeverClampedOrPresentedAsZeroChance() = runTest {
        val controller = DrawProbabilityController(this, StandardTestDispatcher(testScheduler))
        controller.open(2_147_483_648L); controller.calculate(); runCurrent()
        assertEquals("2147483648", controller.state.value.population)
        assertTrue(controller.state.value.invalid); assertNull(controller.state.value.result)
        controller.population("4"); controller.successes("5"); controller.draws("2"); controller.calculate(); runCurrent()
        assertTrue(controller.state.value.invalid); assertNull(controller.state.value.result)
        controller.successes("0"); controller.calculate(); runCurrent()
        assertFalse(controller.state.value.invalid)
        assertEquals(BigInteger.valueOf(6), checkNotNull(controller.state.value.result).points[0].ways)
    }
}
