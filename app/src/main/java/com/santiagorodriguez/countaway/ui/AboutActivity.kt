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
import com.santiagorodriguez.countaway.data.BackupFlow

class AboutActivity : BaseActivity() {
    private lateinit var flow: BackupFlow
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private var confirmation: AlertDialog? = null
    private var resumed = false
    private var calendarTapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        InsetUtils.applySystemBarPadding(findViewById(R.id.aboutRoot))
        flow = lastNonConfigurationInstance as? BackupFlow ?: BackupFlow(applicationContext, savedInstanceState)
        flow.onChanged = { renderBackup() }
        calendarTapCount = savedInstanceState?.getInt(STATE_CALENDAR_TAPS, 0) ?: 0

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
        findViewById<TextView>(R.id.versionText).text = getString(R.string.about_version_compact, versionName)

        findViewById<View>(R.id.createCountdownAction).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        findViewById<View>(R.id.addWidgetAction).setOnClickListener { showWidgetSetupHint() }
        findViewById<View>(R.id.stopCheckingAction).setOnClickListener { playCalendarMoment() }
        findViewById<View>(R.id.websiteButton).setOnClickListener {
            openExternal(PROJECT_WEBSITE)
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

        renderBackup()
        restoreCalendarMoment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CALENDAR_TAPS, calendarTapCount)
        flow.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_EXPORT || requestCode == REQUEST_IMPORT) {
            flow.pickerResult(if (resultCode == Activity.RESULT_OK) data?.data else null)
        }
    }

    override fun onRetainNonConfigurationInstance(): Any = flow

    override fun onResume() {
        super.onResume()
        resumed = true
        renderBackup()
    }

    override fun onPause() {
        resumed = false
        confirmation?.dismiss()
        confirmation = null
        super.onPause()
    }

    override fun onDestroy() {
        flow.onChanged = null
        if (!isChangingConfigurations) flow.abandon()
        super.onDestroy()
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
        flow.pickExport(resumePendingImport)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "CountAway-backup.json")
        runCatching { startActivityForResult(intent, REQUEST_EXPORT) }
            .onFailure { flow.providerUnavailable() }
    }

    private fun importBackup() {
        flow.pickImport()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
        runCatching { startActivityForResult(intent, REQUEST_IMPORT) }
            .onFailure { flow.providerUnavailable() }
    }

    private fun renderBackup() {
        val idle = flow.stage == BackupFlow.Stage.IDLE
        exportButton.isEnabled = idle
        importButton.isEnabled = idle
        if (!resumed || isFinishing || isDestroyed) return
        flow.takeNotice()?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        if (flow.stage != BackupFlow.Stage.CONFIRM) {
            confirmation?.dismiss()
            confirmation = null
            return
        }
        if (confirmation != null) return
        val message = flow.currentCount?.let {
            getString(R.string.backup_import_confirm_details, flow.incomingCount, it)
        } ?: getString(R.string.backup_import_confirm_details_unknown, flow.incomingCount)
        confirmation = AlertDialog.Builder(this)
            .setTitle(R.string.backup_import_confirm_title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel) { _, _ -> flow.cancelConfirmation() }
            .setNeutralButton(R.string.backup_export_current_first) { _, _ -> exportBackup(true) }
            .setPositiveButton(R.string.backup_import_action) { _, _ -> flow.confirmImport() }
            .setOnCancelListener { flow.cancelConfirmation() }
            .show().also { dialog ->
                DialogPresentation.polish(dialog)
                dialog.setOnDismissListener {
                    if (confirmation === dialog) confirmation = null
                    if (resumed) window.decorView.post { renderBackup() }
                }
            }
    }

    private fun openExternal(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, R.string.external_action_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val PROJECT_WEBSITE = "https://countaway.cajapersonal.org/"
        const val DONATE_WEBSITE = "https://cajapersonal.org/donate/"
        const val REQUEST_EXPORT = 5101
        const val REQUEST_IMPORT = 5102
        const val STATE_CALENDAR_TAPS = "calendar_taps"
    }
}
