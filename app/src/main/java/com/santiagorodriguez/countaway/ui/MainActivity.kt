package com.santiagorodriguez.countaway.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownEventOrder
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.countdown.CountdownTimeSnapshot
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.model.CountdownEvent
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.notification.ArrivalNotifier
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetPinSetupActivity
import com.santiagorodriguez.countaway.widget.WidgetUpdateScheduler
import java.time.LocalDate

class MainActivity : BaseActivity() {
    private lateinit var repository: CountdownRepository
    private lateinit var adapter: CountdownEventAdapter
    private lateinit var eventActions: HomeEventActions
    private lateinit var countdownList: ListView
    private lateinit var emptyState: View
    private lateinit var emptyStateIcon: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDescription: TextView
    private lateinit var addCountdownButton: Button
    private lateinit var languageButton: Button
    private lateinit var languageChooser: LanguageChooser
    private lateinit var renderedLanguage: String
    private var restoreLanguageFocus = false
    private var restoreLanguageAccessibilityFocus = false
    private lateinit var temporalInvalidationController: TemporalInvalidationController
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        InsetUtils.applySystemBarPadding(findViewById(R.id.mainRoot))

        repository = CountdownRepository(this)
        eventActions = HomeEventActions(this,
            lastNonConfigurationInstance as? HomeEventActions.Session ?: HomeEventActions.Session(),
            ::openEditor, ::openWidgetPinSetup,
        ) { refreshTemporalState(CountdownTime.snapshot()) }
        adapter = CountdownEventAdapter(this, ::openWidgetPinSetup, ::openEditor, eventActions::show)
        countdownList = findViewById(R.id.countdownList)
        emptyState = findViewById(R.id.emptyState)
        emptyStateIcon = findViewById(R.id.emptyStateIcon)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptyDescription = findViewById(R.id.emptyDescription)
        addCountdownButton = findViewById(R.id.addCountdownButton)
        languageButton = findViewById(R.id.languageButton)
        languageChooser = LanguageChooser(this, languageButton)
        renderedLanguage = LanguageManager.currentLanguageTag(this)
        restoreLanguageFocus = savedInstanceState?.getBoolean(STATE_LANGUAGE_FOCUS) ?: false
        restoreLanguageAccessibilityFocus = savedInstanceState?.getBoolean(STATE_LANGUAGE_ACCESSIBILITY_FOCUS) ?: false
        temporalInvalidationController = TemporalInvalidationController(this, ::refreshTemporalState)

        countdownList.adapter = adapter
        countdownList.emptyView = emptyState
        countdownList.setOnItemClickListener { _, _, position, _ -> openEditor(adapter.getItem(position)) }
        countdownList.setOnItemLongClickListener { _, _, position, _ ->
            openWidgetPinSetup(adapter.getItem(position))
            true
        }

        addCountdownButton.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        findViewById<View>(R.id.themeButton).setOnClickListener { showThemePicker() }
        findViewById<View>(R.id.aboutButton).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onRetainNonConfigurationInstance(): Any = eventActions.session

    override fun onResume() {
        super.onResume()
        if (LanguageManager.currentLanguageTag(this) != renderedLanguage) {
            recreate()
            return
        }
        val snapshot = CountdownTime.snapshot()
        temporalInvalidationController.start(snapshot)
        refreshTemporalState(snapshot)
        eventActions.resume()
        languageChooser.render()
        renderThemeButton()
        if (restoreLanguageFocus || restoreLanguageAccessibilityFocus) {
            languageButton.requestFocus()
            if (restoreLanguageAccessibilityFocus) {
                languageButton.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            }
            restoreLanguageFocus = false
            restoreLanguageAccessibilityFocus = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_LANGUAGE_FOCUS, languageButton.hasFocus())
        outState.putBoolean(STATE_LANGUAGE_ACCESSIBILITY_FOCUS, languageButton.isAccessibilityFocused)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        eventActions.pause()
        temporalInvalidationController.stop()
        loadGeneration += 1
        super.onPause()
    }

    override fun onDestroy() {
        eventActions.close()
        languageChooser.dismiss()
        super.onDestroy()
    }

    private fun refreshTemporalState(snapshot: CountdownTimeSnapshot) {
        val generation = ++loadGeneration
        setAddEnabled(false)
        countdownList.isEnabled = false
        val context = applicationContext

        CountdownIo.submit(
            task = {
                val result = repository.loadResult()
                if (result is CountdownLoadResult.Success) {
                    ArrivalNotifier.reconcileVisibleNotifications(context, result.events, snapshot)
                }
                result
            },
            onComplete = { result ->
                if (generation != loadGeneration || isFinishing || isDestroyed) return@submit
                renderData(
                    result.getOrElse { CountdownLoadResult.Failure(CountdownDataProblem.CORRUPT) },
                    snapshot.today,
                )
            },
        )

        CountdownIo.execute {
            runCatching { CountdownWidgetProvider.updateAllWidgets(context, snapshot) }
            runCatching { WidgetUpdateScheduler.ensureScheduled(context, snapshot) }
            runCatching { ArrivalNotificationScheduler.ensureScheduled(context, snapshot) }
        }
    }

    private fun renderData(result: CountdownLoadResult, today: LocalDate) {
        when (result) {
            is CountdownLoadResult.Success -> {
                adapter.submit(CountdownEventOrder.sortedForDisplay(result.events, today), today)
                emptyStateIcon.visibility = View.VISIBLE
                emptyTitle.setText(R.string.empty_title)
                emptyDescription.setText(R.string.empty_description)
                countdownList.isEnabled = true
                setAddEnabled(true)
            }
            is CountdownLoadResult.Failure -> {
                adapter.submit(emptyList(), today)
                emptyStateIcon.visibility = View.GONE
                if (result.problem == CountdownDataProblem.UNSUPPORTED_SCHEMA) {
                    emptyTitle.setText(R.string.data_newer_version_title)
                    emptyDescription.setText(R.string.data_newer_version_description)
                } else {
                    emptyTitle.setText(R.string.data_error_title)
                    emptyDescription.setText(R.string.data_error_description)
                }
                countdownList.isEnabled = false
                setAddEnabled(false)
            }
        }
    }

    private fun openEditor(event: CountdownEvent) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_EVENT_ID, event.id))
    }

    private fun openWidgetPinSetup(event: CountdownEvent) {
        startActivity(
            Intent(this, WidgetPinSetupActivity::class.java)
                .putExtra(WidgetPinSetupActivity.EXTRA_EVENT_ID, event.id),
        )
    }

    private fun setAddEnabled(enabled: Boolean) {
        addCountdownButton.isEnabled = enabled
        addCountdownButton.alpha = if (enabled) 1f else 0.45f
    }

    private fun showThemePicker() {
        val themes = ThemeManager.AppTheme.entries
        val labels = arrayOf(
            getString(R.string.widget_appearance_system),
            getString(R.string.widget_appearance_light),
            getString(R.string.widget_appearance_dark),
        )
        val selected = themes.indexOf(ThemeManager.currentTheme(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.widget_appearance_label)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                dialog.dismiss()
                ThemeManager.setTheme(this, themes[which])
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun renderThemeButton() {
        val button = findViewById<TextView>(R.id.themeButton)
        when (ThemeManager.currentTheme(this)) {
            ThemeManager.AppTheme.SYSTEM -> {
                button.text = "◐"
                button.contentDescription = getString(R.string.theme_follow_system)
            }
            ThemeManager.AppTheme.LIGHT -> {
                button.text = "☀"
                button.contentDescription = getString(R.string.widget_appearance_light)
            }
            ThemeManager.AppTheme.DARK -> {
                button.text = "☾"
                button.contentDescription = getString(R.string.widget_appearance_dark)
            }
        }
    }

    private companion object {
        const val STATE_LANGUAGE_FOCUS = "language_focus"
        const val STATE_LANGUAGE_ACCESSIBILITY_FOCUS = "language_accessibility_focus"
    }
}