package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.santiagorodriguez.countaway.R
import com.santiagorodriguez.countaway.data.CountdownDataException
import com.santiagorodriguez.countaway.data.CountdownDataProblem
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.data.CountdownStorageCodec
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.notification.ArrivalNotificationState
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetUpdateScheduler

class AboutActivity : BaseActivity() {
    private lateinit var repository: CountdownRepository
    private var pendingImportUri: Uri? = null
    private var pendingImportCount: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        InsetUtils.applySystemBarPadding(findViewById(R.id.aboutRoot))
        repository = CountdownRepository(this)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
        findViewById<TextView>(R.id.versionText).text = getString(R.string.about_version, versionName)

        findViewById<Button>(R.id.websiteButton).setOnClickListener {
            openExternal(PERSONAL_WEBSITE)
        }
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportBackup() }
        findViewById<Button>(R.id.importButton).setOnClickListener { importBackup() }
        findViewById<Button>(R.id.donateButton).setOnClickListener {
            openExternal(DONATE_WEBSITE)
        }

        restorePendingImport(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingImportUri?.let { uri ->
            outState.putString(STATE_PENDING_IMPORT_URI, uri.toString())
        }
        pendingImportCount?.let { count ->
            outState.putInt(STATE_PENDING_IMPORT_COUNT, count)
        }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQUEST_EXPORT -> writeBackup(uri)
            REQUEST_IMPORT -> readBackup(uri)
        }
    }

    private fun exportBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "CountAway-backup.json")
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun importBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    private fun writeBackup(uri: Uri) {
        try {
            val payload = repository.exportPayload()
            contentResolver.openOutputStream(uri, "rwt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(payload)
            } ?: error("Unable to open destination")
            Toast.makeText(this, R.string.backup_export_success, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.backup_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun readBackup(uri: Uri) {
        clearPendingImport()
        try {
            val payload = readUtf8Payload(uri)
            val count = repository.previewImport(payload)
            pendingImportUri = uri
            pendingImportCount = count
            showImportConfirmation(count)
        } catch (error: CountdownDataException) {
            showImportValidationError(error)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showImportConfirmation(count: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_import_confirm_title)
            .setMessage(resources.getQuantityString(R.plurals.backup_import_confirm_message, count, count))
            .setNegativeButton(R.string.action_cancel) { _, _ -> clearPendingImport() }
            .setPositiveButton(R.string.backup_import_action) { _, _ -> confirmImport() }
            .setOnCancelListener { clearPendingImport() }
            .show()
    }

    private fun restorePendingImport(state: Bundle?) {
        val rawUri = state?.getString(STATE_PENDING_IMPORT_URI) ?: return
        if (!state.containsKey(STATE_PENDING_IMPORT_COUNT)) return

        pendingImportUri = Uri.parse(rawUri)
        pendingImportCount = state.getInt(STATE_PENDING_IMPORT_COUNT)
        showImportConfirmation(pendingImportCount ?: return)
    }

    private fun readUtf8Payload(uri: Uri): String {
        val stream = contentResolver.openInputStream(uri) ?: error("Unable to open backup")
        return stream.use(CountdownStorageCodec::readUtf8Payload)
    }

    private fun confirmImport() {
        val uri = pendingImportUri ?: return
        clearPendingImport()

        try {
            val payload = readUtf8Payload(uri)
            repository.importPayload(payload)
        } catch (error: CountdownDataException) {
            showImportValidationError(error)
            return
        } catch (_: Exception) {
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
            return
        }

        ArrivalNotificationState(this).clear()
        runCatching { CountdownWidgetProvider.updateAllWidgets(this) }
        runCatching { WidgetUpdateScheduler.ensureScheduled(this) }
        runCatching { ArrivalNotificationScheduler.ensureScheduled(this) }
        Toast.makeText(this, R.string.backup_import_success, Toast.LENGTH_SHORT).show()
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
        pendingImportUri = null
        pendingImportCount = null
    }

    private fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private companion object {
        const val PERSONAL_WEBSITE = "https://santiagorodriguez.com"
        const val DONATE_WEBSITE = "https://santiagorodriguez.com/donate"
        const val REQUEST_EXPORT = 5101
        const val REQUEST_IMPORT = 5102
        const val STATE_PENDING_IMPORT_URI = "pending_import_uri"
        const val STATE_PENDING_IMPORT_COUNT = "pending_import_count"
    }
}
