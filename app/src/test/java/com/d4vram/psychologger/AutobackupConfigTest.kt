package com.d4vram.psychologger

import org.junit.Assert.assertEquals
import org.junit.Test

class AutobackupConfigTest {
    @Test fun defaultsAreLogical() {
        assertEquals(12, BackupPreferences.DEFAULT_INTERVAL_HOURS)
        assertEquals(7, BackupPreferences.DEFAULT_KEEP_COUNT)
    }

    @Test fun normalizesSupportedIntervalsAndFallsBackToDefault() {
        for (h in listOf(6, 12, 24, 48, 168)) {
            assertEquals(h, BackupPreferences.normalizeIntervalHours(h))
        }
        assertEquals(12, BackupPreferences.normalizeIntervalHours(0))
        assertEquals(12, BackupPreferences.normalizeIntervalHours(-5))
        assertEquals(12, BackupPreferences.normalizeIntervalHours(99))
    }

    @Test fun normalizesSupportedKeepCountsAndFallsBackToDefault() {
        for (c in listOf(3, 5, 7, 10, 15)) {
            assertEquals(c, BackupPreferences.normalizeKeepCount(c))
        }
        assertEquals(7, BackupPreferences.normalizeKeepCount(0))
        assertEquals(7, BackupPreferences.normalizeKeepCount(-1))
        assertEquals(7, BackupPreferences.normalizeKeepCount(100))
    }
}
