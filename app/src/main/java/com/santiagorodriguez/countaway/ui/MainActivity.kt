package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetUpdateScheduler
import java.time.LocalDate

class MainActivity : Activity() {
    private lateinit var repository: CountdownRepository
    private lateinit var adapter: CountdownEventAdapter
    private lateinit var countdownList: ListView
    private lateinit var emptyState: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = CountdownRepository(this)
        adapter = CountdownEventAdapter(this)
        countdownList = findViewById(R.id.countdownList)
        emptyState = findViewById(R.id.emptyState)

        countdownList.adapter = adapter
        countdownList.emptyView = emptyState
        countdownList.setOnItemClickListener { _, _, position, _ ->
            val event = adapter.getItem(position)
            startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_EVENT_ID, event.id))
        }

        findViewById<Button>(R.id.addCountdownButton).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        findViewById<Button>(R.id.aboutButton).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val today = LocalDate.now()
        val events = CountdownEventOrder.sortedForDisplay(repository.load(), today)
        adapter.submit(events, today)
        CountdownWidgetProvider.updateAllWidgets(this)
        WidgetUpdateScheduler.ensureScheduled(this)
    }
}
