package com.santiagorodriguez.countaway.ui

import java.time.LocalTime

/** Validate before native focus changes can overwrite the selected time. */
internal object TimePickerCommitPolicy {
    fun canAccept(
        proposed: LocalTime,
        starting: LocalTime,
        initiallyValid: Boolean,
        validBeforeFocusLoss: Boolean,
        validAfterFocusLoss: Boolean,
        textEdited: Boolean,
    ): Boolean {
        // Reject an invalid edited field, even if native focus loss restores
        // a previously valid hour underneath the dialog.
        if (textEdited && !validAfterFocusLoss) return false
        return validBeforeFocusLoss ||
            (initiallyValid && !textEdited && proposed == starting)
    }
}
