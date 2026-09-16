package com.garsyanimultiusaha.gmuedutrans.sales

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun MarketingKitScreen(vm: SalesViewModel) {
    val loggedIn = vm.state as? SalesAppState.LoggedIn
    if (loggedIn == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    MarketingKitScreen(
        session = loggedIn.session,
        onClose = { vm.navigate(SalesPage.DASHBOARD) }
    )
}
