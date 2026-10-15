package com.zendolauncher.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

/**
 * The single calendar-permission action used by Upcoming and the Calendar
 * screen. The system dialog fires only from an explicit tap, exactly once per
 * platform rules: after a denial with "don't ask again" the platform would
 * auto-deny a re-fired dialog without showing anything, which reads as a
 * repeat-ask loop. In that case the action routes to the app Settings page
 * instead, where the user can grant or leave it denied — both calm end states.
 */
@Composable
fun CalendarAccessAction(viewModel: LauncherViewModel, askedBefore: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onCalendarPermissionResult(granted)
        if (granted) viewModel.refreshCalendar()
    }
    val permanentlyDenied = askedBefore && activity?.let {
        !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CALENDAR)
    } ?: false
    if (permanentlyDenied) {
        SecondaryText("Calendar access was declined. You can allow it anytime in Settings.")
        QuietAction("Open Settings to allow calendar", onClick = { openAppSettings(context) })
    } else {
        QuietAction("Allow calendar access", onClick = {
            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        })
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
