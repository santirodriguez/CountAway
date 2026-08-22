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
import com.santiagorodriguez.countaway.data.CountdownRepository
import com.santiagorodriguez.countaway.notification.ArrivalNotificationScheduler
import com.santiagorodriguez.countaway.widget.CountdownWidgetProvider
import com.santiagorodriguez.countaway.widget.WidgetUpdateScheduler

class AboutActivity : BaseActivity() {
    private lateinit var repository: CountdownRepository
    private var pendingImportPayload: String? = null

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
            contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(payload)
            } ?: error("Unable to open destination")
            Toast.makeText(this, R.string.backup_export_success, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.backup_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun readBackup(uri: Uri) {
        try {
            val payload = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Unable to open backup")
            val count = repository.previewImport(payload)
            pendingImportPayload = payload
            AlertDialog.Builder(this)
                .setTitle(R.string.backup_import_confirm_title)
                .setMessage(resources.getQuantityString(R.plurals.backup_import_confirm_message, count, count))
                .setNegativeButton(R.string.action_cancel) { _, _ -> pendingImportPayload = null }
                .setPositiveButton(R.string.backup_import_action) { _, _ -> confirmImport() }
                .setOnCancelListener { pendingImportPayload = null }
                .show()
        } catch (_: CountdownDataException) {
            Toast.makeText(this, R.string.backup_import_invalid, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmImport() {
        val payload = pendingImportPayload ?: return
        pendingImportPayload = null
        try {
            repository.importPayload(payload)
            CountdownWidgetProvider.updateAllWidgets(this)
            WidgetUpdateScheduler.ensureScheduled(this)
            ArrivalNotificationScheduler.ensureScheduled(this)
            Toast.makeText(this, R.string.backup_import_success, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private companion object {
        const val PERSONAL_WEBSITE = "https://santiagorodriguez.com"
        const val DONATE_WEBSITE = "https://santiagorodriguez.com/donate"
        const val REQUEST_EXPORT = 5101
        const val REQUEST_IMPORT = 5102
    }
}
