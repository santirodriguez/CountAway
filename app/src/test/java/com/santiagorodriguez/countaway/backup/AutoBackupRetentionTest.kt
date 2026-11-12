package com.santiagorodriguez.countaway.backup

import org.junit.Assert.*
import org.junit.Test

class AutoBackupRetentionTest {
    @Test fun onlyTheOldestOwnedEntriesAreEligibleForPruning() {
        val all = (1..10).map { n ->
            AutoBackupStore.OwnedCopy("content://provider/owned/$n", "sha$n", n.toLong())
        }
        val stale = AutoBackupRetention.stale(all)
        assertEquals(listOf(1L, 2L, 3L), stale.map { it.createdAt })
        assertEquals(7, all.size - stale.size)
    }

    @Test fun fewerThanSevenCopiesLeaveEverythingUntouched() {
        val entries = (1..7).map { n ->
            AutoBackupStore.OwnedCopy("content://provider/$n", "digest", n.toLong())
        }
        assertTrue(AutoBackupRetention.stale(entries).isEmpty())
        assertTrue(AutoBackupRetention.stale(emptyList()).isEmpty())
    }
}
