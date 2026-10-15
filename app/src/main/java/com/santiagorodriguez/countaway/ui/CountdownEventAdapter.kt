package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalStage
import com.santiagorodriguez.countaway.countdown.CountUpState
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.EventCountResolver
import com.santiagorodriguez.countaway.countdown.TimedCountDirection
import com.santiagorodriguez.countaway.countdown.TimedEventResolver
import com.santiagorodriguez.countaway.data.EventTransport
import com.santiagorodriguez.countaway.model.CountMode
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.LocalDate
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class CountdownEventAdapter(
    private val context: Context,
    private val onAddWidget: ((CountdownEvent) -> Unit)? = null,
    private val onOpenEvent: ((CountdownEvent) -> Unit)? = null,
    private val onEventActions: ((CountdownEvent, View) -> Unit)? = null,
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private var items: List<CountdownEvent> = emptyList()
    private var today: LocalDate = CountdownTime.snapshot().today
    private var currentTime: ZonedDateTime = CountdownTime.snapshot().now
    private var showSectionHeaders = false

    fun submit(events: List<CountdownEvent>, today: LocalDate) {
        this.items = events
        this.today = today
        currentTime = CountdownTime.snapshot().now
        showSectionHeaders = events.asSequence()
            .map { sectionFor(EventCountResolver.resolve(it, today)) }
            .distinct()
            .take(2)
            .count() > 1
        notifyDataSetChanged()
    }

    fun hasTimedEvents(): Boolean = items.any { it.eventTime != null }

    /** Only visible clock labels are updated; no list reload, disk writes or widget work. */
    fun tickVisible(list: ListView, now: ZonedDateTime) {
        currentTime = now
        for (index in 0 until list.childCount) {
            val eventIndex = list.firstVisiblePosition + index
            val event = items.getOrNull(eventIndex) ?: continue
            if (event.eventTime == null) continue
            val row = list.getChildAt(index) as? HomeEventRow ?: continue
            val text = timeText(event, now)
            val detail = row.findViewById<TextView>(R.id.eventTimeDetail)
            if (detail.text.toString() != text) {
                detail.text = text
                val statusView = row.findViewById<TextView>(R.id.eventStatus)
                val days = timeDaysText(event, now)
                if (statusView.text.toString() != days) statusView.text = days
                statusView.contentDescription = days
                val title = EventTransport.preview(event.title)
                val meta = row.findViewById<TextView>(R.id.eventMeta).text
                val status = row.findViewById<TextView>(R.id.eventStatus).contentDescription
                row.contentDescription = listOf(title, meta, timeAccessibilityText(event, now), status).joinToString(", ")
            }
        }
    }

    private fun timeDaysText(event: CountdownEvent, now: ZonedDateTime): String {
        val value = TimedEventResolver.resolve(event, now) ?: return ""
        val quantity = value.days.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val localized = NumberFormat.getIntegerInstance(
            context.resources.configuration.locales[0]).format(value.days)
        return context.resources.getQuantityString(
            R.plurals.timed_days_magnitude, quantity, localized)
    }

    private fun timeText(event: CountdownEvent, now: ZonedDateTime): String =
        TimedEventResolver.resolve(event, now)?.subday() ?: ""

    private fun timeAccessibilityText(event: CountdownEvent, now: ZonedDateTime): String {
        val value = TimedEventResolver.resolve(event, now) ?: return ""
        val resource = when (value.direction) {
            TimedCountDirection.REMAINING -> R.string.timed_remaining
            TimedCountDirection.STARTS_IN -> R.string.timed_starts_in
            TimedCountDirection.ELAPSED -> R.string.timed_elapsed
        }
        return context.getString(resource, value.subday())
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): CountdownEvent = items[position]
    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = (convertView as? HomeEventRow)
            ?: inflater.inflate(R.layout.item_countdown, parent, false) as HomeEventRow
        val event = getItem(position)
        val timed = TimedEventResolver.resolve(event, currentTime)
        view.bindTimedCompact(timed != null)
        val original = EventCountResolver.resolve(event, today)
        val value = when {
            timed == null -> original
            event.countMode == CountMode.COUNT_DOWN ->
                EventCountResolver.resolve(timed.occurrenceDate, RepeatRule.NONE, event.countMode, today)
            else -> original.copy(
                displayDate = timed.occurrenceDate,
                magnitude = timed.days,
                countUpState = if (timed.direction == TimedCountDirection.STARTS_IN)
                    CountUpState.STARTS_IN else CountUpState.ELAPSED,
            )
        }
        view.foreground = null
        val locale = context.resources.configuration.locales[0]
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

        view.findViewById<ImageView>(R.id.eventIcon).apply {
            setImageResource(EventIconPresentation.drawableRes(event.icon))
            contentDescription = context.getString(EventIconPresentation.labelRes(event.icon))
        }
        view.findViewById<TextView>(R.id.eventTitle).apply {
            text = EventTransport.preview(event.title)
            maxLines = if (timed == null) Int.MAX_VALUE else 2
            ellipsize = if (timed == null) null else TextUtils.TruncateAt.END
            contentDescription = event.title
        }
        val eventType = context.getString(EventTypePresentation.labelRes(event.type))
        val dateText = value.displayDate.format(dateFormatter)
        val formattedDate = if (timed != null && event.eventTime != null) {
            val clock = event.eventTime.format(
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
            val zone = if (timed.target.zone != currentTime.zone) {
                " · " + timed.target.format(DateTimeFormatter.ofPattern("z", locale))
            } else ""
            dateText + " · " + clock + zone
        } else dateText
        val meta = when {
            value.countUpState != null && timed != null -> context.getString(
                if (value.countUpState == CountUpState.STARTS_IN)
                    R.string.home_start_date else R.string.home_since_date, formattedDate)
            value.countUpState != null -> HomeEventText.startDate(context, value)
            event.repeatRule == RepeatRule.NONE -> context.getString(R.string.event_meta, eventType, formattedDate)
            else -> context.getString(R.string.event_meta_repeating, eventType, formattedDate,
                context.getString(repeatLabelRes(event.repeatRule)))
        }
        view.findViewById<TextView>(R.id.eventMeta).text = meta
        view.findViewById<TextView>(R.id.eventTimeDetail).apply {
            visibility = if (timed == null) View.GONE else View.VISIBLE
            text = if (timed == null) "" else timeText(event, currentTime)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        }
        val statusView = view.findViewById<TextView>(R.id.eventStatus).apply {
            animate().withEndAction(null).cancel()
            text = if (timed == null) HomeEventText.status(context, value)
                else timeDaysText(event, currentTime)
            contentDescription = if (timed == null) EventCountText.status(context, value)
                else timeDaysText(event, currentTime)
            setTypeface(typeface, Typeface.BOLD)
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
        ArrivalIllustration.bindHome(context, view.findViewById(R.id.eventArrival),
            ArrivalStage.from(value.countdownStatus))
        val sectionDescription = bindSectionHeader(view, position, value)
        view.contentDescription = listOfNotNull(sectionDescription, EventTransport.preview(event.title), meta,
            if (timed != null) timeAccessibilityText(event, currentTime) else null, statusView.contentDescription)
            .joinToString(", ")
        bindAddWidgetAction(view, event)
        bindRowActions(view, event)
        return view
    }

    private fun bindSectionHeader(view: HomeEventRow, position: Int, value: com.santiagorodriguez.countaway.countdown.EventCountValue): String? {
        val section = sectionFor(value)
        val previous = if (position > 0) sectionFor(EventCountResolver.resolve(items[position - 1], today)) else null
        if (!showSectionHeaders || previous == section) {
            view.bindSectionHeader(null, null)
            return null
        }
        val (labelRes, accessibilityRes) = when (section) {
            HomeSection.UPCOMING -> R.string.home_section_upcoming to R.string.home_section_upcoming_accessibility
            HomeSection.COUNT_UP -> R.string.home_section_count_up to R.string.home_section_count_up_accessibility
            HomeSection.PAST -> R.string.home_section_past to R.string.home_section_past_accessibility
        }
        val label = context.getString(labelRes)
        val accessibility = context.getString(accessibilityRes)
        view.bindSectionHeader(label, accessibility)
        return accessibility
    }

    private fun sectionFor(value: com.santiagorodriguez.countaway.countdown.EventCountValue): HomeSection = when {
        value.countUpState != null -> HomeSection.COUNT_UP
        value.countdownStatus == com.santiagorodriguez.countaway.countdown.CountdownStatus.DONE -> HomeSection.PAST
        else -> HomeSection.UPCOMING
    }

    private enum class HomeSection { UPCOMING, COUNT_UP, PAST }

    private fun bindRowActions(view: View, event: CountdownEvent) {
        view.setOnClickListener(onOpenEvent?.let { open ->
            View.OnClickListener { if (interactionEnabled(view)) open(event) }
        })
        view.isFocusable = onOpenEvent != null
        view.setOnLongClickListener(onAddWidget?.let { add ->
            View.OnLongClickListener {
                if (!interactionEnabled(view)) return@OnLongClickListener false
                add(event)
                true
            }
        })
        view.findViewById<View>(R.id.eventActions).apply {
            tag = event.id
            visibility = if (onEventActions == null) View.GONE else View.VISIBLE
            contentDescription = context.getString(R.string.event_actions_description, EventTransport.preview(event.title))
            setOnClickListener {
                if (interactionEnabled(this)) onEventActions?.invoke(event, this)
            }
        }
    }

    private fun bindAddWidgetAction(view: View, event: CountdownEvent) {
        val action = onAddWidget
        if (action == null) {
            view.accessibilityDelegate = null
            return
        }
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_LONG_CLICK,
                    context.getString(R.string.widget_add_accessibility_action),
                ))
            }
            override fun performAccessibilityAction(host: View, actionId: Int, arguments: Bundle?): Boolean {
                if (actionId == AccessibilityNodeInfo.ACTION_LONG_CLICK && interactionEnabled(host)) {
                    action(event)
                    return true
                }
                return super.performAccessibilityAction(host, actionId, arguments)
            }
        }
    }

    private fun interactionEnabled(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (!current.isEnabled) return false
            current = current.parent as? View
        }
        return true
    }

    private fun repeatLabelRes(repeatRule: RepeatRule): Int = when (repeatRule) {
        RepeatRule.NONE -> R.string.repeat_never
        RepeatRule.WEEKLY -> R.string.repeat_weekly
        RepeatRule.MONTHLY -> R.string.repeat_monthly
        RepeatRule.YEARLY -> R.string.repeat_yearly
    }
}
