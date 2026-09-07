package com.pamoyanan.one

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pamoyanan.one.ui.PamoyananApp
import com.pamoyanan.one.ui.theme.PamoyananTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PamoyananTheme {
                PamoyananApp()
            }
        }
    }
}
