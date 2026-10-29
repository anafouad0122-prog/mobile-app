package com.fouad.cuttingnotebook

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import com.fouad.cuttingnotebook.ui.AppRoot
import com.fouad.cuttingnotebook.ui.AppViewModel
import com.fouad.cuttingnotebook.ui.theme.CuttingNotebookTheme

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels {
        AppViewModel.factory((application as CuttingNotebookApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keeps production records out of the recent-apps thumbnail and off screenshots
        // (spec 13).
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            val settings by appViewModel.settings.collectAsState()
            CuttingNotebookTheme(themeMode = settings.themeMode, accent = settings.accent) {
                // The whole interface is Arabic, so it is laid out right-to-left regardless
                // of the phone's own language (spec 12).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppRoot(
                        appViewModel = appViewModel,
                        container = (application as CuttingNotebookApp).container,
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app re-arms the lock, so the notebook is never left open on a bench.
        appViewModel.lockNow()
    }
}
