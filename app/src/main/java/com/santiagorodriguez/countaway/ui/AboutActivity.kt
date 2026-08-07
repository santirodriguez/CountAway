package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.santiagorodriguez.countaway.R

class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
        findViewById<TextView>(R.id.versionText).text = getString(R.string.about_version, versionName)

        findViewById<Button>(R.id.websiteButton).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PERSONAL_WEBSITE)))
        }
    }

    private companion object {
        const val PERSONAL_WEBSITE = "https://santiagorodriguez.com"
    }
}
