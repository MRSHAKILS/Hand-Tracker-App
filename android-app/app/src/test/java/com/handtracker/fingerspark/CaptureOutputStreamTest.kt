package com.handtracker.fingerspark

import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import org.junit.Assert.*
import org.junit.Test

class CaptureOutputStreamTest {
    @Test fun activeCaptureWritesTheRequestedByteRange() {
        val destination = ByteArrayOutputStream()
        CaptureOutputStream(destination) { true }.use {
            it.write(byteArrayOf(1, 2, 3, 4), 1, 2)
        }
        assertArrayEquals(byteArrayOf(2, 3), destination.toByteArray())
    }

    @Test fun stoppingPlayPreventsFurtherBytesFromBeingWritten() {
        val gate = TrackingSession()
        val session = gate.start()
        val destination = ByteArrayOutputStream()
        val stream = CaptureOutputStream(destination) { gate.isCurrent(session) }
        stream.write(1)
        gate.stop()
        assertThrows(InterruptedIOException::class.java) { stream.write(byteArrayOf(2, 3)) }
        assertArrayEquals(byteArrayOf(1), destination.toByteArray())
    }

    @Test fun switchingCameraCancelsThePreviousCapture() {
        val gate = TrackingSession()
        val original = gate.start()
        val destination = ByteArrayOutputStream()
        val stream = CaptureOutputStream(destination) { gate.isCurrent(original) }
        gate.start()
        assertThrows(InterruptedIOException::class.java) { stream.write(1) }
        assertEquals(0, destination.size())
    }

    @Test fun closingTheWrapperClosesItsDestination() {
        var closed = false
        val destination = object : ByteArrayOutputStream() {
            override fun close() { closed = true }
        }
        CaptureOutputStream(destination) { true }.close()
        assertTrue(closed)
    }
}
