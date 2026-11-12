package com.santiagorodriguez.countaway.ui

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoBackupFolderGrantPolicyTest {
    private val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    private val tree = "content://com.example.documents/tree/folder"

    @Test fun newlyGrantedReadWritePermissionsAreReleasedAfterFailure() {
        assertEquals(read or write, AutoBackupFolderGrantPolicy.releaseFlags(
            hadRead = false, hadWrite = false,
            destinationEnabled = false, committedTreeUri = null, attemptedTreeUri = tree))
    }

    @Test fun preexistingReadPermissionIsPreservedOnFailure() {
        assertEquals(write, AutoBackupFolderGrantPolicy.releaseFlags(
            hadRead = true, hadWrite = false,
            destinationEnabled = true, committedTreeUri = "content://other",
            attemptedTreeUri = tree))
    }

    @Test fun existingReadWriteGrantIsNeverReleasedForFailedReselection() {
        assertEquals(0, AutoBackupFolderGrantPolicy.releaseFlags(
            hadRead = true, hadWrite = true,
            destinationEnabled = true, committedTreeUri = "content://other",
            attemptedTreeUri = tree))
    }

    @Test fun currentCommittedDestinationKeepsAllPermissions() {
        assertEquals(0, AutoBackupFolderGrantPolicy.releaseFlags(
            hadRead = false, hadWrite = false,
            destinationEnabled = true, committedTreeUri = tree, attemptedTreeUri = tree))
    }

    @Test fun disabledFormerDestinationDoesNotRetainNewGrant() {
        assertEquals(read or write, AutoBackupFolderGrantPolicy.releaseFlags(
            hadRead = false, hadWrite = false,
            destinationEnabled = false, committedTreeUri = tree, attemptedTreeUri = tree))
    }
}
