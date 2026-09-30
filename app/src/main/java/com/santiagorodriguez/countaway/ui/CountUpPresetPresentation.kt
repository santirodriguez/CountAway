package com.santiagorodriguez.countaway.ui

import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.model.CountUpPreset

internal object CountUpPresetPresentation {
    fun labelRes(preset: CountUpPreset): Int = when (preset) {
        CountUpPreset.SMOKE_FREE -> R.string.preset_smoke_free
        CountUpPreset.NEW_HABIT -> R.string.preset_new_habit
        CountUpPreset.TRAINING -> R.string.preset_training
        CountUpPreset.LEARNING -> R.string.preset_learning
        CountUpPreset.PROJECT -> R.string.preset_project
        CountUpPreset.NEW_JOB -> R.string.preset_new_job
        CountUpPreset.NEW_HOME -> R.string.preset_new_home
        CountUpPreset.EVENT, CountUpPreset.CUSTOM -> EventTypePresentation.labelRes(preset.type)
    }
}
