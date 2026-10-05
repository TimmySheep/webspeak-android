package com.echosixhiya.webspeak.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Context
import com.echosixhiya.webspeak.android.locale.AppLocaleContext
import com.echosixhiya.webspeak.android.ui.WebSpeakApp
import com.echosixhiya.webspeak.android.ui.theme.WebSpeakTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleContext.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebSpeakTheme {
                WebSpeakApp()
            }
        }
    }
}
