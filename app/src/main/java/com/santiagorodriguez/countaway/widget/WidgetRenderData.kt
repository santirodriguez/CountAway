package com.santiagorodriguez.countaway.widget

import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate

internal sealed interface WidgetRenderData {
    fun resolve(configuration: WidgetConfiguration?): CountdownEvent?

    data class Ready(
        val eventsById: Map<String, CountdownEvent>,
        val nextEvent: CountdownEvent?,
    ) : WidgetRenderData {
        override fun resolve(configuration: WidgetConfiguration?): CountdownEvent? {
            configuration ?: return null
            return when (configuration.eventSelection) {
                WidgetEventSelection.FIXED -> configuration.eventId?.let(eventsById::get)
                WidgetEventSelection.NEXT -> nextEvent
            }
        }
    }

    data class Failure(
        val problem: com.santiagorodriguez.countaway.data.CountdownDataProblem,
    ) : WidgetRenderData {
        override fun resolve(configuration: WidgetConfiguration?): CountdownEvent? = null
    }

    companion object {
        fun from(loadResult: CountdownLoadResult, today: LocalDate): WidgetRenderData =
            when (loadResult) {
                is CountdownLoadResult.Failure -> Failure(loadResult.problem)
                is CountdownLoadResult.Success -> Ready(
                    eventsById = loadResult.events.associateBy(CountdownEvent::id),
                    nextEvent = WidgetEventResolver.resolveNext(loadResult.events, today),
                )
            }
    }
}
