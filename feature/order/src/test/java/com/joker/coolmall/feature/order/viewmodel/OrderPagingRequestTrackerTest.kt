package com.joker.coolmall.feature.order.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderPagingRequestTrackerTest {

    @Test
    fun `new request only invalidates older request in same tab`() {
        val tracker = OrderPagingRequestTracker(tabCount = 3)
        val firstTabRequest = tracker.startRequest(tabIndex = 0, page = 1)
        val secondTabRequest = tracker.startRequest(tabIndex = 1, page = 1)

        val replacement = tracker.startRequest(tabIndex = 0, page = 1)

        assertFalse(tracker.isCurrent(firstTabRequest))
        assertTrue(tracker.isCurrent(replacement))
        assertTrue(tracker.isCurrent(secondTabRequest))
    }

    @Test
    fun `stale response cannot advance committed page`() {
        val tracker = OrderPagingRequestTracker(tabCount = 1)
        val stalePageTwo = tracker.startRequest(tabIndex = 0, page = 2)
        val refresh = tracker.startRequest(tabIndex = 0, page = 1)

        assertFalse(tracker.commit(stalePageTwo))
        assertTrue(tracker.commit(refresh))
        assertEquals(2, tracker.nextPage(tabIndex = 0))
    }

    @Test
    fun `each tab advances its own page independently`() {
        val tracker = OrderPagingRequestTracker(tabCount = 2)
        val firstTabPageTwo = tracker.startRequest(tabIndex = 0, page = 2)
        val secondTabPageThree = tracker.startRequest(tabIndex = 1, page = 3)

        assertTrue(tracker.commit(firstTabPageTwo))
        assertTrue(tracker.commit(secondTabPageThree))

        assertEquals(3, tracker.nextPage(tabIndex = 0))
        assertEquals(4, tracker.nextPage(tabIndex = 1))
    }

    @Test
    fun `invalidating tab rejects its in flight response`() {
        val tracker = OrderPagingRequestTracker(tabCount = 2)
        val request = tracker.startRequest(tabIndex = 1, page = 2)

        tracker.invalidate(tabIndex = 1)

        assertFalse(tracker.isCurrent(request))
        assertFalse(tracker.commit(request))
        assertEquals(2, tracker.nextPage(tabIndex = 1))
    }
}
