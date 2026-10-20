package com.handtracker.fingerspark

import org.junit.Assert.*
import org.junit.Test

class PreviewGeometryTest {
    @Test fun imageCenterMapsToViewportCenter() {
        val geometry = PreviewGeometry(720f, 1480f, 640, 480)
        assertEquals(360f, geometry.screenX(0.5f), 0.01f)
        assertEquals(740f, geometry.screenY(0.5f), 0.01f)
    }

    @Test fun matchingAspectRatioHasNoCrop() {
        val geometry = PreviewGeometry(720f, 1280f, 360, 640)
        assertEquals(0f, geometry.offsetX, 0.01f)
        assertEquals(0f, geometry.offsetY, 0.01f)
    }

    @Test fun targetsRemainVisibleAcrossPortraitAndLandscapeCrops() {
        val viewports = listOf(720f to 1480f, 320f to 480f, 1080f to 2400f, 1600f to 900f)
        val sources = listOf(640 to 480, 480 to 640, 1920 to 1080)
        for ((width, height) in viewports) {
            for ((sourceWidth, sourceHeight) in sources) {
                val geometry = PreviewGeometry(width, height, sourceWidth, sourceHeight)
                for (fraction in listOf(0f, 0.5f, 1f)) {
                    val x = geometry.screenX(geometry.targetX(fraction))
                    val y = geometry.screenY(geometry.targetY(fraction))
                    assertTrue("Target x=$x outside width=$width", x in 0f..width)
                    assertTrue("Target y=$y outside height=$height", y in 0f..height)
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroSizedImages() {
        PreviewGeometry(720f, 1480f, 0, 480)
    }

    @Test fun missesOutsideVisibleRingDoNotScoreOnCroppedFrames() {
        val geometry = PreviewGeometry(720f, 1480f, 640, 480)
        assertFalse(geometry.isTargetHit(0.58f, 0.5f, 0.5f, 0.5f, 52f))
        assertFalse(geometry.isTargetHit(0.5f, 0.58f, 0.5f, 0.5f, 52f))
    }

    @Test fun hitDistanceIsCircularInScreenPixels() {
        val geometry = PreviewGeometry(720f, 1480f, 640, 480)
        assertTrue(geometry.isTargetHit(0.5f + 40f / geometry.drawnWidth, 0.5f, 0.5f, 0.5f, 52f))
        assertTrue(geometry.isTargetHit(0.5f, 0.5f + 40f / geometry.drawnHeight, 0.5f, 0.5f, 52f))
        assertFalse(geometry.isTargetHit(0.5f + 40f / geometry.drawnWidth,
            0.5f + 40f / geometry.drawnHeight, 0.5f, 0.5f, 52f))
    }

    @Test fun fingertipOutsideVisibleViewportCannotScore() {
        val geometry = PreviewGeometry(720f, 1480f, 640, 480)
        assertFalse(geometry.isTargetHit(0f, 0.5f, 0f, 0.5f, 52f))
    }

    @Test fun invalidTrackingCoordinatesCannotScore() {
        val geometry = PreviewGeometry(720f, 1480f, 640, 480)
        assertFalse(geometry.isTargetHit(Float.NaN, 0.5f, 0.5f, 0.5f, 52f))
        assertFalse(geometry.isTargetHit(Float.POSITIVE_INFINITY, 0.5f, 0.5f, 0.5f, 52f))
    }
}
