package com.santiagorodriguez.countaway.ui

import android.content.Intent

/** Revoke only permissions newly obtained for an uncommitted SAF destination. */
internal object AutoBackupFolderGrantPolicy {
    fun releaseFlags(
        hadRead: Boolean,
        hadWrite: Boolean,
        stillCommitted: Boolean,
    ): Int {
        if (stillCommitted) return 0
        return (if (hadRead) 0 else Intent.FLAG_GRANT_READ_URI_PERMISSION) or
            (if (hadWrite) 0 else Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}
