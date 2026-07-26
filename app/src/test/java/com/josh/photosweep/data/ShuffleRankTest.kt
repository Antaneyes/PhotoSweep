package com.josh.photosweep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleRankTest {
    @Test
    fun rankIsStableAndNonNegative() {
        val first = MediaDatabase.shuffleRank("media-A")
        assertEquals(first, MediaDatabase.shuffleRank("media-A"))
        assertNotEquals(first, MediaDatabase.shuffleRank("media-B"))
        assertTrue(first >= 0)
    }
}
