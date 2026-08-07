package com.santiagorodriguez.countaway.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle

abstract class BaseActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageManager.syncPlatformLocale(this)
    }
}
