package com.runnow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.runnow.app.ui.RunNowApp

/** Layar utama native (Compose). Menggantikan MainActivity (WebView) sebagai peluncur; MainActivity dihapus di Langkah 12. */
class RunNowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RunNowApp() }
    }
}
