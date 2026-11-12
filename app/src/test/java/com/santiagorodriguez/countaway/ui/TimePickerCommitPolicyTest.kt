package com.santiagorodriguez.countaway.ui

import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimePickerCommitPolicyTest {
    private val initial = LocalTime.of(18, 30)
    private val changed = LocalTime.of(21, 45)

    @Test fun acceptsValidEditedTime() {
        assertTrue(TimePickerCommitPolicy.canAccept(changed, initial,
            initiallyValid = true, validBeforeFocusLoss = true,
            validAfterFocusLoss = true, textEdited = false))
    }

    @Test fun nativeFocusChangeCannotLoseValidSelection() {
        assertTrue(TimePickerCommitPolicy.canAccept(changed, initial,
            initiallyValid = true, validBeforeFocusLoss = true,
            validAfterFocusLoss = false, textEdited = false))
    }

    @Test fun invalidTypedInputCannotUseTheInitialSelectionFallback() {
        assertFalse(TimePickerCommitPolicy.canAccept(initial, initial,
            initiallyValid = true, validBeforeFocusLoss = false,
            validAfterFocusLoss = false, textEdited = true))
    }

    @Test fun typedInputMustValidateAfterFocusLoss() {
        assertFalse(TimePickerCommitPolicy.canAccept(changed, initial,
            initiallyValid = true, validBeforeFocusLoss = true,
            validAfterFocusLoss = false, textEdited = true))
    }

    @Test fun invalidNewSelectionIsRejected() {
        assertFalse(TimePickerCommitPolicy.canAccept(changed, initial,
            initiallyValid = true, validBeforeFocusLoss = false,
            validAfterFocusLoss = true, textEdited = false))
    }

    @Test fun untouchedValidInitialTimeStillWorks() {
        assertTrue(TimePickerCommitPolicy.canAccept(initial, initial,
            initiallyValid = true, validBeforeFocusLoss = false,
            validAfterFocusLoss = false, textEdited = false))
    }
}
