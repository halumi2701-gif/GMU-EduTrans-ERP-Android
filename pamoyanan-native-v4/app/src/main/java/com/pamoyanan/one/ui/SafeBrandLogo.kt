package com.pamoyanan.one.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SafeBrandLogo(
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember {
        runCatching {
            val b64 = context.assets.open("logo_rw01.b64").bufferedReader().use { it.readText() }
            val bytes = Base64.decode(b64.trim(), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Logo RW 01 Pamoyanan",
            modifier = modifier.then(Modifier),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.AccountBalance,
            contentDescription = "Pamoyanan ONE",
            tint = Color(0xFF0B3D2E),
            modifier = modifier
        )
    }
}
