package com.santiagorodriguez.countaway.ui

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownCalculator
import com.santiagorodriguez.countaway.countdown.CountdownStatus
import com.santiagorodriguez.countaway.model.CountdownEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class CountdownEventAdapter(private val context: Context) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
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

        view.findViewById<TextView>(R.id.eventIcon).text = EventTypePresentation.icon(event.type)
        view.findViewById<TextView>(R.id.eventTitle).text = event.title
        view.findViewById<TextView>(R.id.eventMeta).text = context.getString(
            R.string.event_meta,
            context.getString(EventTypePresentation.labelRes(event.type)),
            event.date.format(dateFormatter),
        )

        view.findViewById<TextView>(R.id.eventStatus).apply {
            text = when (countdown.status) {
                CountdownStatus.FUTURE -> context.getString(R.string.status_days, countdown.days)
                CountdownStatus.TOMORROW -> context.getString(R.string.status_tomorrow)
                CountdownStatus.TODAY -> context.getString(R.string.status_today)
                CountdownStatus.DONE -> context.getString(R.string.status_done)
            }
            setTypeface(typeface, Typeface.BOLD)
        }

        return view
    }
}
