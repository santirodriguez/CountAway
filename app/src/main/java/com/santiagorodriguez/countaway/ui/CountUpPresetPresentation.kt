package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.CountUpPreset
import com.santiagorodriguez.countaway.model.EventIcon

internal object CountUpPresetPresentation {
    fun labelRes(preset: CountUpPreset): Int = when (preset) {
        CountUpPreset.SMOKE_FREE -> R.string.preset_smoke_free
        CountUpPreset.NEW_HABIT -> R.string.preset_new_habit
        CountUpPreset.TRAINING -> R.string.preset_training
        CountUpPreset.READING -> R.string.preset_reading
        CountUpPreset.PROJECT -> R.string.preset_project
        CountUpPreset.FRESH_START -> R.string.preset_fresh_start
        CountUpPreset.MILESTONE -> R.string.preset_milestone
        CountUpPreset.EVENT, CountUpPreset.CUSTOM -> EventTypePresentation.labelRes(preset.type)
    }

    /** Catalog artwork does not change the icon stored in an event. */
    fun glyphRes(preset: CountUpPreset): Int = when (preset) {
        CountUpPreset.SMOKE_FREE -> R.drawable.editor_smoke_free
        CountUpPreset.NEW_HABIT -> R.drawable.editor_sprout
        CountUpPreset.TRAINING -> R.drawable.editor_training
        CountUpPreset.READING -> EventIconPresentation.drawableRes(EventIcon.BOOK)
        CountUpPreset.PROJECT -> R.drawable.editor_project
        CountUpPreset.FRESH_START -> R.drawable.editor_sunrise
        CountUpPreset.MILESTONE -> EventIconPresentation.drawableRes(EventIcon.FLAG)
        CountUpPreset.EVENT -> EventIconPresentation.drawableRes(EventIcon.CALENDAR)
        CountUpPreset.CUSTOM -> R.drawable.editor_customize
    }
}
