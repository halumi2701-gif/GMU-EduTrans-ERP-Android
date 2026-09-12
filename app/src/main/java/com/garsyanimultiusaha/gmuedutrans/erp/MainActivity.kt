package com.garsyanimultiusaha.gmuedutrans.erp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private var launchTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        launchTarget = intent?.getStringExtra("target_page")

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            PushNotifications.isConfigured() &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4201)
        }

        setContent {
            val vm: MainViewModel = viewModel()
            LaunchedEffect(launchTarget, vm.state) {
                if (launchTarget == "WORKFLOW" && vm.state is AppState.LoggedIn) {
                    vm.navigate(AppPage.WORKFLOW)
                    launchTarget = null
                }
            }
            GmuNativeAppWithPicActionInbox(vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchTarget = intent.getStringExtra("target_page")
    }
}
