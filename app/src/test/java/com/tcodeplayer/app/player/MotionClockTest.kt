package com.tcodeplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionClockTest {

    @Test
    fun `absolute deadline compensates short body time`() {
        val step = 20_000_000L
        val prev = 1_000L
        // 本轮只过了 8ms，下次仍应在 prev+20ms 醒，而不是 now+20ms（会漂移）
        assertEquals(prev + step, MotionDriveLoop.ClockMath.nextDeadline(prev, prev + 8_000_000L, step))
    }

    @Test
    fun `long stall resets clock instead of catching up`() {
        val step = 20_000_000L
        val prev = 1_000L
        val now = prev + 5 * step
        assertEquals(now + step, MotionDriveLoop.ClockMath.nextDeadline(prev, now, step))
    }
}
