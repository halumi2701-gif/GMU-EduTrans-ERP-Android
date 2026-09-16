package com.garsyanimultiusaha.gmuedutrans.sales

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SalesRoot(vm: SalesViewModel) {
    var showMarketingKit by rememberSaveable { mutableStateOf(false) }
    val loggedIn = vm.state as? SalesAppState.LoggedIn

    LaunchedEffect(loggedIn?.session?.userId) {
        if (loggedIn == null) showMarketingKit = false
    }

    Box(Modifier.fillMaxSize()) {
        SalesApp(vm)

        if (loggedIn != null && !showMarketingKit) {
            ExtendedFloatingActionButton(
                onClick = { showMarketingKit = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 92.dp),
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Text("Marketing Kit")
            }
        }

        if (loggedIn != null && showMarketingKit) {
            MarketingKitScreen(
                session = loggedIn.session,
                onClose = { showMarketingKit = false }
            )
        }
    }
}
