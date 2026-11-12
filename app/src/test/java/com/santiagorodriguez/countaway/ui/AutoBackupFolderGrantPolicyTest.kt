package com.santiagorodriguez.countaway.ui

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoBackupFolderGrantPolicyTest {
    private val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    @Test fun newlyGrantedReadWritePermissionsAreReleasedAfterFailure() {
        assertEquals(read or write, AutoBackupFolderGrantPolicy.releaseFlags(
            hadRead = false, hadWrite = false, stillCommitted = false))
    }

    @Test fun preexistingReadPermissionIsPreservedOnFailure() {
        assertEquals(write, AutoBackupFolderGrantPolicy.releaseFlags(
            hadRead = true, hadWrite = false, stillCommitted = false))
    }

    @Test fun existingReadWriteGrantIsNeverReleasedForFailedReselection() {
        assertEquals(0, AutoBackupFolderGrantPolicy.releaseFlags(
            hadRead = true, hadWrite = true, stillCommitted = false))
    }

    @Test fun currentCommittedDestinationKeepsAllPermissions() {
        assertEquals(0, AutoBackupFolderGrantPolicy.releaseFlags(
            hadRead = false, hadWrite = false, stillCommitted = true))
    }
}
