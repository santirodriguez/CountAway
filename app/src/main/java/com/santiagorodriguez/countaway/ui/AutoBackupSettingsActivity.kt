package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.backup.AutoBackupEngine
import com.santiagorodriguez.countaway.backup.AutoBackupStore
import com.santiagorodriguez.countaway.backup.AutoBackupScheduler
import com.santiagorodriguez.countaway.data.RetainedOperation
import java.text.DateFormat
import java.util.Date

/** Opt-in local event snapshots, intentionally separate from the existing manual Import/Export. */
class AutoBackupSettingsActivity : BaseActivity() {
    private class Session {
        val work = RetainedOperation<AutoBackupEngine.Outcome>()
    }

    private lateinit var session: Session
    private var resumed = false
    private var choosingLegacyDefault = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_backup_settings)
        InsetUtils.applySystemBarPadding(findViewById(R.id.autoBackupRoot))
        // Apply the wrapped light/dark theme to SystemUI before the first frame.
        applyStatusBarContrast()
        session = lastNonConfigurationInstance as? Session ?: Session()
        session.work.onChanged = ::consumeResult
        choosingLegacyDefault = savedInstanceState?.getBoolean(STATE_LEGACY_PICKER) ?: false

        findViewById<View>(R.id.autoBackupBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.autoBackupDefault).setOnClickListener { defaultDestination() }
        findViewById<Button>(R.id.autoBackupChoose).setOnClickListener { chooseFolder(false) }
        findViewById<Button>(R.id.autoBackupNow).setOnClickListener {
            startWork { AutoBackupEngine.backupNow(applicationContext) }
        }
        findViewById<Button>(R.id.autoBackupRecover).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.auto_backup_recover_title)
                .setMessage(R.string.auto_backup_recover_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.auto_backup_recover) { _, _ ->
                    runCatching {
                        AutoBackupStore.resetUnreadable(applicationContext)
                        AutoBackupScheduler.cancel(applicationContext)
                    }.onFailure { toast(R.string.auto_backup_settings_failed) }
                    render()
                }
                .show().also { DialogPresentation.polish(it) }
        }
        findViewById<Button>(R.id.autoBackupDisable).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.auto_backup_disable_title)
                .setMessage(R.string.auto_backup_disable_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.auto_backup_disable) { _, _ ->
                    runCatching {
                        AutoBackupEngine.disable(applicationContext)
                        AutoBackupScheduler.cancel(applicationContext)
                    }.onFailure { toast(R.string.auto_backup_settings_failed) }
                    render()
                }
                .show().also { DialogPresentation.polish(it) }
        }
        render()
    }

    override fun onRetainNonConfigurationInstance(): Any = session

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_LEGACY_PICKER, choosingLegacyDefault)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarContrast()
        refreshStatusBarOnNextFrame()
        resumed = true
        consumeResult()
        runCatching { AutoBackupScheduler.ensureScheduled(applicationContext) }
        render()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyStatusBarContrast()
            refreshStatusBarOnNextFrame()
        }
    }

    private fun refreshStatusBarOnNextFrame() {
        window.decorView.postOnAnimation {
            if (!isFinishing && !isDestroyed) applyStatusBarContrast()
        }
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        session.work.onChanged = null
        super.onDestroy()
    }

    private fun defaultDestination() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            AlertDialog.Builder(this)
                .setTitle(R.string.auto_backup_legacy_title)
                .setMessage(R.string.auto_backup_legacy_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.auto_backup_choose) { _, _ -> chooseFolder(true) }
                .show().also { DialogPresentation.polish(it) }
            return
        }
        startWork {
            AutoBackupEngine.enable(applicationContext,
                AutoBackupStore.Destination(AutoBackupStore.Kind.DEFAULT_DOWNLOADS))
        }
    }

    private fun chooseFolder(legacyDefault: Boolean) {
        if (session.work.running) return
        choosingLegacyDefault = legacyDefault
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(intent, REQUEST_FOLDER) }
            .onFailure { toast(R.string.auto_backup_provider_failed) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FOLDER || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val grants = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (grants != (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
            toast(R.string.auto_backup_permission_failed)
            return
        }
        if (runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.isFailure) {
            toast(R.string.auto_backup_permission_failed)
            return
        }

        val folderName = runCatching {
            DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/')
                .substringAfterLast(':').take(60).ifBlank { "Folder" }
        }.getOrDefault(getString(R.string.auto_backup_chosen_folder))
        val label = if (choosingLegacyDefault) getString(R.string.auto_backup_legacy_label, folderName)
            else folderName
        startWork {
            AutoBackupEngine.enable(applicationContext,
                AutoBackupStore.Destination(AutoBackupStore.Kind.SELECTED_FOLDER,
                    treeUri = uri.toString(), label = label))
        }
        choosingLegacyDefault = false
    }

    private fun startWork(task: () -> AutoBackupEngine.Outcome) {
        if (session.work.running) return
        session.work.startDocument(task)
        render()
    }

    private fun consumeResult() {
        if (!resumed || isFinishing || isDestroyed) return
        session.work.takeResult()?.fold(
            onSuccess = {
                runCatching { AutoBackupScheduler.ensureScheduled(applicationContext) }
                toast(if (it.created) R.string.auto_backup_verified else R.string.auto_backup_unchanged)
            },
            onFailure = {
                val error = runCatching { AutoBackupStore.read(applicationContext).lastError }
                    .getOrNull()
                toast(if (error == "DESTINATION_CHANGE_FAILED")
                    R.string.auto_backup_folder_change_failed
                else R.string.auto_backup_provider_failed)
            },
        )
        render()
    }

    private fun render() {
        if (!::session.isInitialized) return
        val isBusy = session.work.running
        for (id in listOf(R.id.autoBackupDefault, R.id.autoBackupChoose, R.id.autoBackupNow,
                R.id.autoBackupDisable)) findViewById<View>(id).isEnabled = !isBusy
        findViewById<View>(R.id.autoBackupProgress).visibility = if (isBusy) View.VISIBLE else View.GONE
        val state = runCatching { AutoBackupStore.read(applicationContext) }.getOrElse {
            // An unreadable configuration cannot be overwritten by a normal
            // destination selection; recovery must be an explicit user action.
            findViewById<View>(R.id.autoBackupDefault).isEnabled = false
            findViewById<View>(R.id.autoBackupChoose).isEnabled = false
            findViewById<TextView>(R.id.autoBackupStatus).setText(R.string.auto_backup_settings_failed)
            findViewById<View>(R.id.autoBackupRecover).visibility = View.VISIBLE
            findViewById<View>(R.id.autoBackupNow).visibility = View.GONE
            findViewById<View>(R.id.autoBackupDisable).visibility = View.GONE
            findViewById<View>(R.id.autoBackupStatusIcon).visibility = View.GONE
            findViewById<View>(R.id.autoBackupVerifiedBadge).visibility = View.GONE
            return
        }
        findViewById<View>(R.id.autoBackupRecover).visibility = View.GONE
        val verified = state.enabled && state.lastVerified > 0L
        findViewById<View>(R.id.autoBackupStatusIcon).visibility =
            if (verified) View.VISIBLE else View.GONE
        findViewById<View>(R.id.autoBackupVerifiedBadge).visibility =
            if (verified) View.VISIBLE else View.GONE
        val defaultChoice = findViewById<Button>(R.id.autoBackupDefault)
        val selectedChoice = findViewById<Button>(R.id.autoBackupChoose)
        defaultChoice.isSelected = state.enabled &&
            state.destination?.kind == AutoBackupStore.Kind.DEFAULT_DOWNLOADS
        selectedChoice.isSelected = state.enabled &&
            state.destination?.kind == AutoBackupStore.Kind.SELECTED_FOLDER
        defaultChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            R.drawable.ic_backup_folder, 0,
            if (defaultChoice.isSelected) R.drawable.ic_backup_check_circle else 0, 0)
        selectedChoice.setCompoundDrawablesRelativeWithIntrinsicBounds(
            R.drawable.ic_backup_folder_plus, 0,
            if (selectedChoice.isSelected) R.drawable.ic_backup_check_circle else 0, 0)
        findViewById<TextView>(R.id.autoBackupStatus).setText(
            if (state.enabled) R.string.auto_backup_on else R.string.auto_backup_off)
        findViewById<View>(R.id.autoBackupNow).visibility = if (state.enabled) View.VISIBLE else View.GONE
        findViewById<View>(R.id.autoBackupDisable).visibility = if (state.enabled) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.autoBackupDestination).text =
            if (state.enabled) getString(R.string.auto_backup_current_location,
                state.destination?.label ?: getString(R.string.auto_backup_chosen_folder))
            else getString(R.string.auto_backup_none)
        findViewById<TextView>(R.id.autoBackupLastVerified).text =
            if (state.lastVerified > 0L && state.enabled)
                getString(R.string.auto_backup_last_success, formatDate(state.lastVerified))
            else getString(R.string.auto_backup_never)
        findViewById<TextView>(R.id.autoBackupLastAttempt).text =
            if (state.lastAttempt > 0L && state.enabled)
                getString(R.string.auto_backup_last_attempt, formatDate(state.lastAttempt))
            else ""
        val error = findViewById<TextView>(R.id.autoBackupError)
        error.visibility = if (state.lastError != null) View.VISIBLE else View.GONE
        error.text = when (state.lastError) {
            "CLEANUP_FAILED" -> getString(R.string.auto_backup_cleanup_warning)
            "SCHEDULE_FAILED" -> getString(R.string.auto_backup_schedule_warning)
            "DESTINATION_CHANGE_FAILED" -> getString(R.string.auto_backup_folder_change_failed)
            null -> ""
            else -> getString(R.string.auto_backup_provider_failed)
        }
    }

    /** The Activity's wrapped app theme may differ from the device UI mode.
     * Set icon contrast from the actually rendered content theme, not a stale
     * system-bar appearance left by the previous Activity. */
    private fun applyStatusBarContrast() {
        val lightContent =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
                Configuration.UI_MODE_NIGHT_YES
        // Both platform appearance channels must agree: a legacy decor flag
        // can otherwise override WindowInsetsController on some Android builds.
        @Suppress("DEPRECATION")
        val legacyFlag = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        @Suppress("DEPRECATION")
        val existing = window.decorView.systemUiVisibility
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            if (lightContent) existing or legacyFlag else existing and legacyFlag.inv()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val modernFlag = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            window.insetsController?.setSystemBarsAppearance(
                if (lightContent) modernFlag else 0, modernFlag)
        }
    }

    private fun formatDate(whenUtc: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(whenUtc))

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()

    private companion object {
        const val REQUEST_FOLDER = 7101
        const val STATE_LEGACY_PICKER = "auto_backup_legacy_picker"
    }
}
