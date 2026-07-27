package com.josh.photosweep.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourceTest {
    @Test
    fun existingDatabaseRowsRemainGooglePhotos() {
        assertEquals(MediaSource.GOOGLE_PHOTOS, MediaSource.from(0))
    }

    @Test
    fun deviceSourceRoundTrips() {
        assertEquals(MediaSource.DEVICE, MediaSource.from(MediaSource.DEVICE.value))
    }

    @Test
    fun unknownSourceFallsBackSafely() {
        assertEquals(MediaSource.GOOGLE_PHOTOS, MediaSource.from(99))
    }
}
