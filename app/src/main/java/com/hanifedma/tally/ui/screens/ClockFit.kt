package com.hanifedma.tally.ui.screens

/**
 * Which clock a window has room for.
 *
 * Material's time picker comes in two shapes — the hour and minute above the
 * dial, or beside it — and picks between them by asking one question: is the
 * screen wider than it is tall? That is the wrong question. A tablet lying on
 * its side is wider than it is tall and has 800dp of height to spare; it was
 * handed the sideways clock anyway, which is 508dp across and was being put
 * in a dialog 360dp wide. The dial does not shrink to fit. It was drawn full
 * size over the minutes and cut off by the edge of the dialog, with a third
 * of its numbers on the far side.
 *
 * The question asked here is the one that matters: which shape *fits*, and
 * how well. Everything is in dp, and nothing in this file knows what a
 * screen is, so the answer for any window can be worked out — and tested —
 * without one.
 */
data class ClockFit(
    val shape: Shape,
    /** The dial's diameter. Material draws it at one of three sizes. */
    val dial: Int,
    /** 1 when the dialog fits as drawn, less when it has to be drawn smaller. */
    val scale: Float,
) {
    enum class Shape {
        /** The time above the dial. What a phone held upright shows. */
        Tall,

        /** The time beside the dial, for a window too short for the other. */
        Wide,
    }

    /**
     * The height to offer Material's sideways clock so that it draws this
     * dial. It is not told a size; it is handed a height and decides — the
     * full dial from 384dp up, the middle one from 330dp, the smallest for
     * anything less.
     */
    val offer: Int
        get() = when {
            dial >= DIAL -> 384
            dial >= 238 -> 330
            else -> dial
        }

    companion object {
        // What Material's picker measures out to. None of these are sizes
        // this code chooses — they are what the picker takes, read off its
        // source — and they are here so that a window too small for it can
        // be recognised before it draws itself over its own edges. If a later
        // version of the library changes one, the dialog is still scaled to
        // whatever it actually measures; the worst a stale number does here
        // is pick the second-best shape.
        private const val TIME_WIDTH = 216       // two 96dp boxes and a colon
        private const val TIME_HEIGHT = 80
        private const val AM_PM_BESIDE = 12 + 52 // the toggle, and the gap before it
        private const val GAP = 36               // between the time and the dial
        private const val TALL_FOOT = 24         // under the dial, in the tall shape only
        const val DIAL = 256
        private val DIALS = listOf(DIAL, 238, 200)

        // What this app puts round it: room at the sides, a little above,
        // and a row of buttons underneath. The sideways clock gets less of
        // each, because the window it is for has no height to give away.
        const val SIDE = 24
        const val TALL_HEAD = 24
        const val TALL_TAIL = 12
        const val WIDE_HEAD = 16
        const val WIDE_GAP = 4
        const val WIDE_TAIL = 8
        const val BUTTONS = 48

        /**
         * Below this a clock is a picture of a clock. The numbers round the
         * dial are 48dp targets at full size; at three quarters they are
         * still something a finger can choose between, and not much under.
         */
        const val SMALLEST = 0.75f

        /** How wide the whole dialog is, drawn at full size. */
        fun width(shape: Shape, dial: Int, hours24: Boolean): Int = SIDE * 2 + when (shape) {
            Shape.Tall -> maxOf(TIME_WIDTH + if (hours24) 0 else AM_PM_BESIDE, dial)
            Shape.Wide -> TIME_WIDTH + GAP + dial
        }

        /** And how tall. */
        fun height(shape: Shape, dial: Int): Int = when (shape) {
            Shape.Tall -> TALL_HEAD + TIME_HEIGHT + GAP + dial + TALL_FOOT + BUTTONS + TALL_TAIL
            Shape.Wide -> WIDE_HEAD + dial + WIDE_GAP + BUTTONS + WIDE_TAIL
        }

        /**
         * The clock for a window with [width] by [height] to give a dialog,
         * or null when no clock would be worth drawing in it — in which case
         * the time is typed instead.
         *
         * The shape that needs shrinking least wins, and the tall one wins a
         * draw: it is the one everybody has seen, and with room for either
         * there is no reason to hand a tablet the phone-on-its-side layout.
         */
        fun choose(width: Float, height: Float, hours24: Boolean): ClockFit? {
            fun fit(shape: Shape, dial: Int) = ClockFit(
                shape, dial,
                minOf(
                    1f,
                    width / width(shape, dial, hours24),
                    height / height(shape, dial),
                ),
            )
            // The sideways clock at each size its dial comes in, biggest
            // first, so that a draw between two of them goes to the bigger.
            val best = (listOf(fit(Shape.Tall, DIAL)) + DIALS.map { fit(Shape.Wide, it) })
                .maxByOrNull { it.scale } ?: return null
            return best.takeIf { it.scale >= SMALLEST }
        }
    }
}
