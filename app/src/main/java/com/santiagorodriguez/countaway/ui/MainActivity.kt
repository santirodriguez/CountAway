package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetUpdateScheduler
import java.time.LocalDate

class MainActivity : BaseActivity() {
    private lateinit var repository: CountdownRepository
    private lateinit var adapter: CountdownEventAdapter
    private lateinit var countdownList: ListView
    private lateinit var emptyState: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        InsetUtils.applySystemBarPadding(findViewById(R.id.mainRoot))

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
        findViewById<View>(R.id.themeButton).setOnClickListener {
            ThemeManager.toggle(this)
        }
        findViewById<View>(R.id.aboutButton).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        findViewById<View>(R.id.languageEnglishButton).setOnClickListener {
            selectLanguage(LanguageManager.ENGLISH)
        }
        findViewById<View>(R.id.languageSpanishButton).setOnClickListener {
            selectLanguage(LanguageManager.SPANISH)
        }
        findViewById<View>(R.id.languageCatalanButton).setOnClickListener {
            selectLanguage(LanguageManager.CATALAN)
        }
    }

    override fun onResume() {
        super.onResume()
        val today = LocalDate.now()
        val events = CountdownEventOrder.sortedForDisplay(repository.load(), today)
        adapter.submit(events, today)
        renderLanguageSelection()
        renderThemeToggle()
        CountdownWidgetProvider.updateAllWidgets(this)
        WidgetUpdateScheduler.ensureScheduled(this)
        ArrivalNotificationScheduler.ensureScheduled(this)
    }

    private fun selectLanguage(languageTag: String) {
        LanguageManager.setLanguage(this, languageTag)
    }

    private fun renderLanguageSelection() {
        val current = LanguageManager.currentLanguageTag(this)
        setLanguageButtonState(R.id.languageEnglishButton, current == LanguageManager.ENGLISH)
        setLanguageButtonState(R.id.languageSpanishButton, current == LanguageManager.SPANISH)
        setLanguageButtonState(R.id.languageCatalanButton, current == LanguageManager.CATALAN)
    }

    private fun renderThemeToggle() {
        val button = findViewById<TextView>(R.id.themeButton)
        when (ThemeManager.currentTheme(this)) {
            ThemeManager.AppTheme.DARK -> {
                button.text = "☀"
                button.contentDescription = getString(R.string.theme_switch_to_light)
            }
            ThemeManager.AppTheme.LIGHT -> {
                button.text = "☾"
                button.contentDescription = getString(R.string.theme_switch_to_dark)
            }
        }
    }

    private fun setLanguageButtonState(viewId: Int, selected: Boolean) {
        findViewById<View>(viewId).setBackgroundResource(
            if (selected) R.drawable.language_chip_active else R.drawable.language_chip_inactive,
        )
    }
}
