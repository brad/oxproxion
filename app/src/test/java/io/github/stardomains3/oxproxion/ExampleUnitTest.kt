package io.github.stardomains3.oxproxion

import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testVersionComparison() {
        // Test basic version increases
        assertTrue(UpdateManager.isNewerVersion("2.1.101", "2.1.102"))
        assertTrue(UpdateManager.isNewerVersion("2.1.99", "2.1.100"))
        assertTrue(UpdateManager.isNewerVersion("2.1.101", "2.2.0"))
        assertTrue(UpdateManager.isNewerVersion("2.1.101", "3.0.0"))

        // Test with "v" and "V" prefix
        assertTrue(UpdateManager.isNewerVersion("2.1.101", "v2.1.102"))
        assertTrue(UpdateManager.isNewerVersion("v2.1.101", "V2.1.102"))
        assertTrue(UpdateManager.isNewerVersion("V2.1.101", "2.1.102"))

        // Test equal versions
        assertFalse(UpdateManager.isNewerVersion("2.1.101", "2.1.101"))
        assertFalse(UpdateManager.isNewerVersion("v2.1.101", "2.1.101"))
        assertFalse(UpdateManager.isNewerVersion("2.1.101", "v2.1.101"))

        // Test older versions
        assertFalse(UpdateManager.isNewerVersion("2.1.102", "2.1.101"))
        assertFalse(UpdateManager.isNewerVersion("2.2.0", "2.1.101"))
        assertFalse(UpdateManager.isNewerVersion("3.0.0", "2.1.101"))

        // Test with different length parts
        assertTrue(UpdateManager.isNewerVersion("2.1", "2.1.1"))
        assertFalse(UpdateManager.isNewerVersion("2.1.1", "2.1"))
    }
}
