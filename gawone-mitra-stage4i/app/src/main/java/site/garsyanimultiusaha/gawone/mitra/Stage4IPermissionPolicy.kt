package site.garsyanimultiusaha.gawone.mitra

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

internal data class Stage4IPermissionSnapshot(
    val fineLocationGranted: Boolean,
    val coarseLocationGranted: Boolean,
    val notificationsGranted: Boolean
)

internal object Stage4IPermissionPolicy {
    fun snapshot(context: Context): Stage4IPermissionSnapshot =
        Stage4IPermissionSnapshot(
            fineLocationGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED,
            coarseLocationGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED,
            notificationsGranted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        )

    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // Policy:
    // - location is requested only when Mitra taps Online / check-in requires it.
    // - notification permission is requested only from communication UI.
    // - KYC uses Photo Picker: no camera/storage permission at startup.
    // - background location is not requested.
}
