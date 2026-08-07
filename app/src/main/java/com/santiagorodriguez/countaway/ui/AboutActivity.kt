package com.santiagorodriguez.countaway.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.santiagorodriguez.countaway.R

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        InsetUtils.applySystemBarPadding(findViewById(R.id.aboutRoot))

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
        findViewById<TextView>(R.id.versionText).text = getString(R.string.about_version, versionName)

        findViewById<Button>(R.id.websiteButton).setOnClickListener {
            openExternal(PERSONAL_WEBSITE)
        }
        findViewById<Button>(R.id.donateButton).setOnClickListener {
            openExternal(DONATE_WEBSITE)
        }
    }

    private fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private companion object {
        const val PERSONAL_WEBSITE = "https://santiagorodriguez.com"
        const val DONATE_WEBSITE = "https://santiagorodriguez.com/donate"
    }
}
