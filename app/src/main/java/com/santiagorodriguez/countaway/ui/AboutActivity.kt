package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.countdown.CountdownTime
import com.santiagorodriguez.countaway.data.CountdownDataException
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownImportSnapshot
import com.santiagorodriguez.countaway.data.CountdownIo
import com.santiagorodriguez.countaway.data.CountdownLoadResult
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.CountdownStorageCodec
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.notification.ArrivalNotificationState
import com.santiagorodriguez.countaway.notification.ArrivalNotifier
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetUpdateScheduler

class AboutActivity : BaseActivity() {
    private lateinit var repository: CountdownRepository
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private var importSnapshot: CountdownImportSnapshot? = null
    private var pendingImportCount: Int? = null
    private var pendingCurrentCount: Int? = null
    private var resumeImportAfterExport = false
    private var calendarTapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        InsetUtils.applySystemBarPadding(findViewById(R.id.aboutRoot))
        repository = CountdownRepository(this)
        calendarTapCount = savedInstanceState?.getInt(STATE_CALENDAR_TAPS, 0) ?: 0

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
        findViewById<TextView>(R.id.versionText).text = getString(R.string.about_version_compact, versionName)

        findViewById<View>(R.id.createCountdownAction).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        findViewById<View>(R.id.addWidgetAction).setOnClickListener { showWidgetSetupHint() }
        findViewById<View>(R.id.stopCheckingAction).setOnClickListener { playCalendarMoment() }
        findViewById<View>(R.id.websiteButton).setOnClickListener {
            openExternal(PERSONAL_WEBSITE)
        }
        findViewById<View>(R.id.privacyButton).setOnClickListener {
            openExternal(getString(R.string.privacy_policy_url))
        }
        exportButton = findViewById(R.id.exportButton)
        importButton = findViewById(R.id.importButton)
        exportButton.setOnClickListener { exportBackup() }
        importButton.setOnClickListener { importBackup() }
        findViewById<Button>(R.id.donateButton).setOnClickListener {
            openExternal(DONATE_WEBSITE)
        }

        restorePendingImport(savedInstanceState)
        restoreCalendarMoment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CALENDAR_TAPS, calendarTapCount)
        val snapshot = importSnapshot
        if (pendingImportCount != null && snapshot != null && snapshot.exists()) {
            outState.putString(STATE_PENDING_IMPORT_ID, snapshot.identity)
        }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_EXPORT && resultCode != Activity.RESULT_OK) {
            val shouldResumeImport = resumeImportAfterExport
            resumeImportAfterExport = false
            if (shouldResumeImport) showPendingImportConfirmation()
            return
        }
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQUEST_EXPORT -> writeBackup(uri)
            REQUEST_IMPORT -> readBackup(uri)
        }
    }

    private fun showWidgetSetupHint() {
        Toast.makeText(this, R.string.about_widget_long_press_hint, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun playCalendarMoment() {
        if (calendarTapCount >= AboutEasterEggPolicy.MAX_TAPS) return

        val action = findViewById<View>(R.id.stopCheckingAction)
        val icon = findViewById<ImageView>(R.id.stopCheckingIcon)
        val reaction = findViewById<TextView>(R.id.stopCheckingReaction)
        calendarTapCount = AboutEasterEggPolicy.nextTapCount(calendarTapCount)
        val milestone = AboutEasterEggPolicy.messageMilestoneForTap(calendarTapCount)

        val haptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        action.performHapticFeedback(haptic)

        val emphasis = when (milestone) {
            100 -> 1.18f
            50 -> 1.14f
            20 -> 1.12f
            10 -> 1.10f
            else -> 1.08f
        }
        val liftDp = when (milestone) {
            100 -> 10f
            50 -> 8f
            else -> 6f
        }

        icon.animate().cancel()
        icon.rotation = 0f
        icon.translationY = 0f
        icon.scaleX = 1f
        icon.scaleY = 1f
        icon.animate()
            .rotation(if (calendarTapCount % 2 == 0) 9f else -9f)
            .translationY(-liftDp * resources.displayMetrics.density)
            .scaleX(emphasis)
            .scaleY(emphasis)
            .setDuration(120L)
            .withEndAction {
                icon.animate()
                    .rotation(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .start()
            }
            .start()

        if (milestone != null) {
            reaction.setText(calendarReactionRes(milestone))
            reaction.visibility = View.VISIBLE
            reaction.alpha = 0f
            reaction.animate().cancel()
            reaction.animate().alpha(1f).setDuration(160L).start()
            reaction.announceForAccessibility(reaction.text)
        }
    }

    private fun restoreCalendarMoment() {
        val milestone = AboutEasterEggPolicy.visibleMilestoneFor(calendarTapCount) ?: return
        findViewById<TextView>(R.id.stopCheckingReaction).apply {
            setText(calendarReactionRes(milestone))
            visibility = View.VISIBLE
            alpha = 1f
        }
    }

    private fun calendarReactionRes(milestone: Int): Int = when (milestone) {
        1 -> R.string.about_calendar_reaction_1
        3 -> R.string.about_calendar_reaction_3
        7 -> R.string.about_calendar_reaction_7
        10 -> R.string.about_calendar_reaction_10
        20 -> R.string.about_calendar_reaction_20
        50 -> R.string.about_calendar_reaction_50
        100 -> R.string.about_calendar_reaction_100
        else -> error("Unsupported Help easter-egg milestone: $milestone")
    }

    private fun exportBackup(resumePendingImport: Boolean = false) {
        resumeImportAfterExport = resumePendingImport
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "CountAway-backup.json")
        runCatching {
            startActivityForResult(intent, REQUEST_EXPORT)
        }.onFailure {
            resumeImportAfterExport = false
            Toast.makeText(this, R.string.backup_provider_unavailable, Toast.LENGTH_LONG).show()
            if (resumePendingImport) showPendingImportConfirmation()
        }
    }

    private fun importBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
        runCatching {
            startActivityForResult(intent, REQUEST_IMPORT)
        }.onFailure {
            Toast.makeText(this, R.string.backup_provider_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun writeBackup(uri: Uri) {
        val shouldResumeImport = resumeImportAfterExport
        resumeImportAfterExport = false
        setBackupBusy(true)
        CountdownIo.submit(
            task = {
                val payload = repository.exportPayload()
                contentResolver.openOutputStream(uri, "rwt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(payload)
                } ?: error("Unable to open destination")
            },
            onComplete = { result ->
                if (isFinishing || isDestroyed) return@submit
                setBackupBusy(false)
                val message = if (result.isSuccess) {
                    R.string.backup_export_success
                } else {
                    R.string.backup_export_failed
                }
                Toast.makeText(
                    this,
                    message,
                    if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()
                if (shouldResumeImport) showPendingImportConfirmation()
            },
        )
    }

    private fun readBackup(uri: Uri) {
        clearPendingImport()
        setBackupBusy(true)
        val context = applicationContext

        CountdownIo.submit(
            task = {
                val payload = readUtf8Payload(uri)
                val count = repository.previewImport(payload)
                val currentCount = when (val current = repository.loadResult()) {
                    is CountdownLoadResult.Success -> current.events.size
                    is CountdownLoadResult.Failure -> null
                }
                val snapshot = CountdownImportSnapshot.create(context)
                try {
                    snapshot.write(payload)
                } catch (error: Exception) {
                    snapshot.clear()
                    throw error
                }
                PendingImport(snapshot, count, currentCount)
            },
            onComplete = { result ->
                if (isFinishing || isDestroyed) return@submit
                setBackupBusy(false)

                result.onSuccess { pending ->
                    setPendingImport(pending)
                    showImportConfirmation(pending)
                }.onFailure { error ->
                    clearPendingImport()
                    showImportFailure(error)
                }
            },
        )
    }

    private fun showImportConfirmation(pending: PendingImport) {
        val message = pending.currentCount?.let { currentCount ->
            getString(
                R.string.backup_import_confirm_details,
                pending.incomingCount,
                currentCount,
            )
        } ?: getString(
            R.string.backup_import_confirm_details_unknown,
            pending.incomingCount,
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.backup_import_confirm_title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel) { _, _ -> clearPendingImport() }
            .setNeutralButton(R.string.backup_export_current_first) { _, _ ->
                exportBackup(resumePendingImport = true)
            }
            .setPositiveButton(R.string.backup_import_action) { _, _ -> confirmImport() }
            .setOnCancelListener { clearPendingImport() }
            .show()
    }

    private fun restorePendingImport(state: Bundle?) {
        val identity = state?.getString(STATE_PENDING_IMPORT_ID) ?: return
        val snapshot = CountdownImportSnapshot.restore(applicationContext, identity) ?: return
        if (!snapshot.exists()) {
            snapshot.clear()
            return
        }

        setBackupBusy(true)
        CountdownIo.submit(
            task = {
                val count = repository.previewImport(snapshot.readPayload())
                val currentCount = when (val current = repository.loadResult()) {
                    is CountdownLoadResult.Success -> current.events.size
                    is CountdownLoadResult.Failure -> null
                }
                PendingImport(snapshot, count, currentCount)
            },
            onComplete = { result ->
                if (isFinishing || isDestroyed) return@submit
                setBackupBusy(false)

                result.onSuccess { pending ->
                    setPendingImport(pending)
                    showImportConfirmation(pending)
                }.onFailure { error ->
                    snapshot.clear()
                    clearPendingImport()
                    showImportFailure(error)
                }
            },
        )
    }

    private fun readUtf8Payload(uri: Uri): String {
        val stream = contentResolver.openInputStream(uri) ?: error("Unable to open backup")
        return stream.use(CountdownStorageCodec::readUtf8Payload)
    }

    private fun confirmImport() {
        val snapshot = importSnapshot
        if (snapshot == null || !snapshot.exists()) {
            clearPendingImport()
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
            return
        }

        pendingImportCount = null
        pendingCurrentCount = null
        setBackupBusy(true)
        val context = applicationContext

        CountdownIo.submit(
            task = {
                try {
                    repository.importPayload(snapshot.readPayload())
                    ArrivalNotificationState(context).clear()
                    ArrivalNotifier.cancelAllEventNotifications(context)
                } finally {
                    snapshot.clear()
                }
            },
            onComplete = { result ->
                if (isFinishing || isDestroyed) return@submit
                importSnapshot = null
                setBackupBusy(false)

                result.onSuccess {
                    refreshBackgroundStateInBackground()
                    Toast.makeText(this, R.string.backup_import_success, Toast.LENGTH_SHORT).show()
                }.onFailure(::showImportFailure)
            },
        )
    }

    private fun setPendingImport(pending: PendingImport) {
        importSnapshot = pending.snapshot
        pendingImportCount = pending.incomingCount
        pendingCurrentCount = pending.currentCount
    }

    private fun showPendingImportConfirmation() {
        val snapshot = importSnapshot ?: return
        val incomingCount = pendingImportCount ?: return
        if (!snapshot.exists()) {
            clearPendingImport()
            return
        }
        showImportConfirmation(
            PendingImport(
                snapshot = snapshot,
                incomingCount = incomingCount,
                currentCount = pendingCurrentCount,
            ),
        )
    }

    private fun showImportFailure(error: Throwable) {
        if (error is CountdownDataException) {
            showImportValidationError(error)
        } else {
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showImportValidationError(error: CountdownDataException) {
        val message = if (error.problem == CountdownDataProblem.UNSUPPORTED_SCHEMA) {
            R.string.backup_import_newer_version
        } else {
            R.string.backup_import_invalid
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun clearPendingImport() {
        pendingImportCount = null
        pendingCurrentCount = null
        importSnapshot?.clear()
        importSnapshot = null
    }

    private fun setBackupBusy(busy: Boolean) {
        exportButton.isEnabled = !busy
        importButton.isEnabled = !busy
    }

    private fun refreshBackgroundStateInBackground() {
        val context = applicationContext
        CountdownIo.execute {
            val snapshot = CountdownTime.snapshot()
            runCatching { CountdownWidgetProvider.updateAllWidgets(context, snapshot) }
            runCatching { WidgetUpdateScheduler.ensureScheduled(context, snapshot) }
            runCatching { ArrivalNotificationScheduler.ensureScheduled(context, snapshot) }
        }
    }

    private fun openExternal(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, R.string.external_action_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private data class PendingImport(
        val snapshot: CountdownImportSnapshot,
        val incomingCount: Int,
        val currentCount: Int?,
    )

    private companion object {
        const val PERSONAL_WEBSITE = "https://santiagorodriguez.com"
        const val DONATE_WEBSITE = "https://santiagorodriguez.com/donate"
        const val REQUEST_EXPORT = 5101
        const val REQUEST_IMPORT = 5102
        const val STATE_PENDING_IMPORT_ID = "pending_import_id"
        const val STATE_CALENDAR_TAPS = "calendar_taps"
    }
}
