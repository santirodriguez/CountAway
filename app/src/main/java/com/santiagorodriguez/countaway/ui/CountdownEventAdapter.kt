package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.ArrivalStage
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.EventCountResolver
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.RepeatRule
import java.time.LocalDate
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

    fun submit(events: List<CountdownEvent>, today: LocalDate) {
        this.items = events
        this.today = today
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): CountdownEvent = items[position]
    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_countdown, parent, false)
        val event = getItem(position)
        val value = EventCountResolver.resolve(event, today)
        view.foreground = null
        val locale = context.resources.configuration.locales[0]
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

        view.findViewById<ImageView>(R.id.eventIcon).apply {
            setImageResource(EventIconPresentation.drawableRes(event.icon))
            contentDescription = context.getString(EventIconPresentation.labelRes(event.icon))
        }
        view.findViewById<TextView>(R.id.eventTitle).text = event.title
        val eventType = context.getString(EventTypePresentation.labelRes(event.type))
        val formattedDate = value.displayDate.format(dateFormatter)
        val meta = when {
            value.countUpState != null -> HomeEventText.startDate(context, value)
            event.repeatRule == RepeatRule.NONE -> context.getString(R.string.event_meta, eventType, formattedDate)
            else -> context.getString(R.string.event_meta_repeating, eventType, formattedDate,
                context.getString(repeatLabelRes(event.repeatRule)))
        }
        view.findViewById<TextView>(R.id.eventMeta).text = meta
        val statusView = view.findViewById<TextView>(R.id.eventStatus).apply {
            animate().withEndAction(null).cancel()
            text = HomeEventText.status(context, value)
            contentDescription = EventCountText.status(context, value)
            setTypeface(typeface, Typeface.BOLD)
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
        }
        ArrivalIllustration.bindHome(context, statusView, ArrivalStage.from(value.countdownStatus))
        view.contentDescription = listOf(event.title, meta, statusView.contentDescription).joinToString(", ")
        bindAddWidgetAction(view, event)
        bindRowActions(view, event)
        return view
    }

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
            contentDescription = context.getString(R.string.event_actions_description, event.title)
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
