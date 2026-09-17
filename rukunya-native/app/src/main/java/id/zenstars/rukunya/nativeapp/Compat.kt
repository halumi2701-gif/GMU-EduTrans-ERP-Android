package id.zenstars.rukunya.nativeapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.RoomDatabase
import androidx.room.withTransaction as roomWithTransaction

val Icons.Filled.Whatsapp: ImageVector
    get() = Icons.Filled.Chat

suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    this.roomWithTransaction(block)
