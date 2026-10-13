package com.mdnotes.widget

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WidgetRefreshTest {
    @Test fun `burst clicks coalesce while another widget remains responsive`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(11)
        val otherFinished = CountDownLatch(1)
        val executions = AtomicInteger()
        try {
            WidgetUpdateCoordinator.runClick(9101, { finished.countDown() }) {
                WidgetUpdateCoordinator.withWidgetLock(9101) {
                    executions.incrementAndGet()
                    started.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repeat(10) {
                WidgetUpdateCoordinator.runClick(9101, { finished.countDown() }) { executions.incrementAndGet() }
            }
            WidgetUpdateCoordinator.runClick(9102, { otherFinished.countDown() }) {
                WidgetUpdateCoordinator.withWidgetLock(9102) { /* Must not wait for widget 9101. */ }
            }
            assertTrue("Other widgets must not wait", otherFinished.await(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
        }
        assertTrue("Every receiver must finish", finished.await(5, TimeUnit.SECONDS))
        assertEquals("A click burst must select only one note", 1, executions.get())

        val nextFinished = CountDownLatch(1)
        WidgetUpdateCoordinator.runClick(9101, { nextFinished.countDown() }) { executions.incrementAndGet() }
        assertTrue(nextFinished.await(5, TimeUnit.SECONDS))
        assertEquals("The next click after completion must work", 2, executions.get())
    }
}
