package com.hanifedma.tally.ui.screens

import com.hanifedma.tally.ui.screens.ClockFit.Shape.Tall
import com.hanifedma.tally.ui.screens.ClockFit.Shape.Wide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which clock a window gets.
 *
 * The windows here are real ones, measured the way the picker measures them:
 * the screen in dp, less the system's bars, less 16dp kept clear all round.
 * The first is the tablet this was written for — the one that was handed a
 * clock 508dp wide to put in a dialog 360dp wide, because it was lying on
 * its side and that was all anyone had asked about it.
 */
class ClockFitTest {

    private fun room(screenWidth: Int, screenHeight: Int, bars: Int = 48) =
        (screenWidth - 32).toFloat() to (screenHeight - bars - 32).toFloat()

    private fun choose(window: Pair<Float, Float>, hours24: Boolean = false) =
        ClockFit.choose(window.first, window.second, hours24)

    @Test
    fun `a tablet on its side has the height for the upright clock, and gets it`() {
        val fit = choose(room(1280, 800))!!
        assertEquals(Tall, fit.shape)
        assertEquals(1f, fit.scale)
    }

    @Test
    fun `a tablet held upright gets the same clock`() {
        val fit = choose(room(800, 1280))!!
        assertEquals(Tall, fit.shape)
        assertEquals(1f, fit.scale)
    }

    @Test
    fun `a phone held upright gets the upright clock at full size`() {
        for ((w, h) in listOf(360 to 640, 360 to 800, 393 to 852, 412 to 915)) {
            val fit = choose(room(w, h, bars = 72))!!
            assertEquals("$w by $h", Tall, fit.shape)
            assertEquals("$w by $h", 1f, fit.scale)
        }
    }

    @Test
    fun `a phone on its side gets the sideways clock, with the biggest dial that fits`() {
        // 360dp of screen leaves 304 for a dialog: the smallest dial, whole.
        val short = choose(room(800, 360, bars = 24))!!
        assertEquals(Wide, short.shape)
        assertEquals(200, short.dial)
        assertEquals(1f, short.scale)

        // A little more, and the middle one fits.
        val middling = choose(room(851, 376, bars = 24))!!
        assertEquals(Wide, middling.shape)
        assertEquals(238, middling.dial)
        assertEquals(1f, middling.scale)

        // 411dp has room for the full one.
        val taller = choose(room(915, 411, bars = 24))!!
        assertEquals(Wide, taller.shape)
        assertEquals(ClockFit.DIAL, taller.dial)
        assertEquals(1f, taller.scale)
    }

    @Test
    fun `the sideways clock is told its dial through the height it is offered`() {
        // Material's own three bands: 384 and up, 330 and up, and the rest.
        assertEquals(384, ClockFit(Wide, 256, 1f).offer)
        assertEquals(330, ClockFit(Wide, 238, 1f).offer)
        assertEquals(200, ClockFit(Wide, 200, 1f).offer)
    }

    @Test
    fun `with room for either, the upright clock wins`() {
        // Plenty of width and exactly enough height for both at full size.
        val fit = ClockFit.choose(2000f, ClockFit.height(Tall, ClockFit.DIAL).toFloat(), false)!!
        assertEquals(Tall, fit.shape)
    }

    @Test
    fun `a narrow phone gets the upright clock a little smaller, not the other one squashed`() {
        // 320dp wide: 288 to give a dialog that wants 328.
        val fit = choose(room(320, 568, bars = 72))!!
        assertEquals(Tall, fit.shape)
        assertTrue(fit.scale < 1f)
        assertTrue(fit.scale >= ClockFit.SMALLEST)
    }

    @Test
    fun `a 24 hour clock has no AM and PM to make room for`() {
        assertTrue(ClockFit.width(Tall, ClockFit.DIAL, hours24 = true) < ClockFit.width(Tall, ClockFit.DIAL, hours24 = false))
        // So a window too narrow for the one at full size can still be wide
        // enough for the other.
        val window = 310f to 600f
        assertEquals(1f, ClockFit.choose(window.first, window.second, hours24 = true)!!.scale)
        assertTrue(ClockFit.choose(window.first, window.second, hours24 = false)!!.scale < 1f)
    }

    @Test
    fun `a window with no room for a clock gets none`() {
        // Half a phone, split with another app: 360 by 300.
        assertNull(choose(room(360, 300, bars = 24)))
        // And nothing at all.
        assertNull(ClockFit.choose(0f, 0f, false))
        assertNull(ClockFit.choose(-32f, -32f, false))
    }

    @Test
    fun `whatever is chosen fits the window it was chosen for`() {
        for (w in 200..1400 step 37) {
            for (h in 200..1400 step 41) {
                for (hours24 in listOf(false, true)) {
                    val fit = ClockFit.choose(w.toFloat(), h.toFloat(), hours24) ?: continue
                    val label = "$w by $h, 24h=$hours24: $fit"
                    assertTrue(label, fit.scale > 0f && fit.scale <= 1f)
                    assertTrue(label, ClockFit.width(fit.shape, fit.dial, hours24) * fit.scale <= w + 0.01f)
                    assertTrue(label, ClockFit.height(fit.shape, fit.dial) * fit.scale <= h + 0.01f)
                }
            }
        }
    }

    @Test
    fun `the sizes here are the ones the picker really takes`() {
        // Read off Material 3's TimePicker.kt, version 1.4.0. If one of
        // these moves, look at the picker before moving the number.
        assertEquals(328, ClockFit.width(Tall, 256, hours24 = false))
        assertEquals(304, ClockFit.width(Tall, 256, hours24 = true))
        assertEquals(480, ClockFit.height(Tall, 256))
        assertEquals(556, ClockFit.width(Wide, 256, hours24 = false))
        assertEquals(332, ClockFit.height(Wide, 256))
        assertNotNull(ClockFit.choose(556f, 332f, false))
    }
}
