package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownCalculator
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.model.EventIcon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class CountdownEventAdapter(private val context: Context) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private val animatedMilestones = mutableSetOf<String>()
    private var items: List<CountdownEvent> = emptyList()
    private var today: LocalDate = LocalDate.now()

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
        val countdown = CountdownCalculator.value(today, event.date)
        val locale = context.resources.configuration.locales[0]
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

        val displayedIcon = if (countdown.status == CountdownStatus.TODAY) EventIcon.CONFETTI else event.icon
        view.findViewById<ImageView>(R.id.eventIcon).apply {
            setImageResource(EventIconPresentation.drawableRes(displayedIcon))
            contentDescription = context.getString(EventIconPresentation.labelRes(displayedIcon))
        }
        view.findViewById<TextView>(R.id.eventTitle).text = event.title
        view.findViewById<TextView>(R.id.eventMeta).text = context.getString(
            R.string.event_meta,
            context.getString(EventTypePresentation.labelRes(event.type)),
            event.date.format(dateFormatter),
        )

        view.findViewById<TextView>(R.id.eventStatus).apply {
            text = when (countdown.status) {
                CountdownStatus.FUTURE -> context.getString(R.string.status_days, countdown.days)
                CountdownStatus.THREE_DAYS -> "✦ 3"
                CountdownStatus.TWO_DAYS -> "✦ 2 ✦"
                CountdownStatus.TOMORROW -> "✦ 1 ✦"
                CountdownStatus.TODAY -> context.getString(R.string.status_today_zero)
                CountdownStatus.DONE -> elapsedStatus(countdown.elapsedDays)
            }
            contentDescription = when (countdown.status) {
                CountdownStatus.FUTURE,
                CountdownStatus.THREE_DAYS,
                CountdownStatus.TWO_DAYS,
                -> context.getString(R.string.status_days, countdown.days)
                CountdownStatus.TOMORROW -> context.getString(R.string.status_tomorrow)
                CountdownStatus.TODAY -> context.getString(R.string.status_today)
                CountdownStatus.DONE -> elapsedStatus(countdown.elapsedDays)
            }
            setTypeface(typeface, Typeface.BOLD)
            scaleX = 1f
            scaleY = 1f
            alpha = 1f

            if (countdown.status in MILESTONE_STATUSES) {
                val animationKey = "${event.id}:${event.date}:${countdown.status}"
                if (animatedMilestones.add(animationKey)) {
                    scaleX = 0.94f
                    scaleY = 0.94f
                    animate()
                        .scaleX(1.07f)
                        .scaleY(1.07f)
                        .setDuration(160)
                        .withEndAction {
                            animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                        }
                        .start()
                }
            }
        }

        return view
    }

    private fun elapsedStatus(elapsedDays: Long): String {
        val quantity = elapsedDays.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return context.resources.getQuantityString(R.plurals.status_days_ago, quantity, elapsedDays)
    }

    private companion object {
        val MILESTONE_STATUSES = setOf(
            CountdownStatus.THREE_DAYS,
            CountdownStatus.TWO_DAYS,
            CountdownStatus.TOMORROW,
            CountdownStatus.TODAY,
        )
    }
}
