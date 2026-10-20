package com.handtracker.fingerspark

import java.io.FilterOutputStream
import java.io.InterruptedIOException
import java.io.OutputStream

/** Stops an in-progress capture when its play session is no longer active. */
internal class CaptureOutputStream(
    output: OutputStream,
    private val isActive: () -> Boolean
) : FilterOutputStream(output) {
    private fun checkActive() {
        if (!isActive()) throw InterruptedIOException("Capture session ended")
    }

    override fun write(value: Int) {
        checkActive()
        out.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        checkActive()
        out.write(buffer, offset, length)
    }
}
