package com.precisiontuner

import com.precisiontuner.update.VersionCompare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun newerPatchVersionIsDetected() {
        assertTrue(VersionCompare.isNewer("1.1.3", "1.1.2"))
        assertTrue(VersionCompare.isNewer("1.2.0", "1.1.9"))
        assertTrue(VersionCompare.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun leadingVAndEqualVersionsAreNotNewer() {
        assertFalse(VersionCompare.isNewer("v1.1.3", "1.1.3"))
        assertFalse(VersionCompare.isNewer("1.1.3", "1.1.3"))
        assertFalse(VersionCompare.isNewer("1.1.2", "1.1.3"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertTrue(VersionCompare.isNewer("1.10", "1.9.9"))
        assertTrue(VersionCompare.isNewer("1.1", "1.0.9"))
        assertFalse(VersionCompare.isNewer("1.1", "1.1.1"))
    }

    @Test
    fun nonNumericSuffixesAreIgnored() {
        assertTrue(VersionCompare.isNewer("1.1.3-beta", "1.1.2"))
        assertEquals(0, VersionCompare.compare("1.1.3-beta", "1.1.3"))
    }
}
