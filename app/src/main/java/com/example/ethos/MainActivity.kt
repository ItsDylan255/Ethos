package com.example.ethos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ethos.data.AppSettings
import com.example.ethos.ui.EthosRoot
import com.example.ethos.ui.theme.EthosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsRepository = (application as EthosApp).container.settings
        setContent {
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            EthosTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                EthosRoot()
            }
        }
    }
}
