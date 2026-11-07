package com.santiagorodriguez.countaway.backup

/** Pure, conservative retention selection. Never scans directories or guesses file ownership. */
internal object AutoBackupRetention {
    const val MAX_VERIFIED_COPIES = 7

    fun stale(copies: List<AutoBackupStore.OwnedCopy>): List<AutoBackupStore.OwnedCopy> =
        copies.sortedWith(compareByDescending<AutoBackupStore.OwnedCopy> { it.createdAt }
            .thenByDescending { it.uri })
            .drop(MAX_VERIFIED_COPIES)
}
