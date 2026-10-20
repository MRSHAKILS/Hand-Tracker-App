package com.handtracker.fingerspark

import org.junit.Assert.*
import org.junit.Test

class TrackingSessionTest {
    @Test fun captureTokensExistOnlyWhilePlayIsActive() {
        val gate = TrackingSession()
        assertNull(gate.currentSession())
        val session = gate.start()
        assertEquals(session, gate.currentSession())
        gate.stop()
        assertNull(gate.currentSession())
    }

    @Test fun onlyOneFrameCanBeInFlight() {
        val gate = TrackingSession()
        val session = gate.start()
        assertTrue(gate.beginFrame(session))
        assertFalse(gate.beginFrame(session))
        assertEquals(session, gate.completeFrame())
        assertTrue(gate.beginFrame(session))
    }

    @Test fun stoppedSessionRejectsQueuedStartupAndFrames() {
        val gate = TrackingSession()
        val session = gate.start()
        gate.stop()
        assertFalse(gate.isCurrent(session))
        assertFalse(gate.beginFrame(session))
    }

    @Test fun cameraSwitchDoesNotMisattributeAnOldResult() {
        val gate = TrackingSession()
        val front = gate.start()
        assertTrue(gate.beginFrame(front))
        val rear = gate.start()
        assertFalse(gate.beginFrame(rear))
        val completed = gate.completeFrame()!!
        assertEquals(front, completed)
        assertFalse(gate.isCurrent(completed))
        assertTrue(gate.beginFrame(rear))
        assertEquals(rear, gate.completeFrame())
    }

    @Test fun rapidExitAndRestartCannotReactivateOldCameraWork() {
        val gate = TrackingSession()
        val first = gate.start()
        assertTrue(gate.beginFrame(first))
        gate.stop()
        val second = gate.start()
        assertEquals(first, gate.completeFrame())
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
        assertFalse(gate.beginFrame(first))
        assertTrue(gate.beginFrame(second))
    }
}
