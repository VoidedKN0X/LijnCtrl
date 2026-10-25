package com.vknxworks.lijnctrl.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.vknxworks.lijnctrl.MainActivity
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.str

/** The app's notification channels, and small helpers shared by the alerts. */
object Notifications {
    /** The ongoing "watching your bus" notification: quiet. */
    const val WATCH = "bus_watch"
    /** "Your bus is almost here": loud. */
    const val BUS = "bus_alert"
    /** Disruptions on saved stops and lines. */
    const val DISRUPTIONS = "disruptions"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(WATCH, str(R.string.channel_watch), NotificationManager.IMPORTANCE_LOW).apply {
                    description = str(R.string.channel_watch_body)
                    setShowBadge(false)
                },
                NotificationChannel(BUS, str(R.string.channel_bus), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = str(R.string.channel_bus_body)
                    enableVibration(true)
                },
                NotificationChannel(DISRUPTIONS, str(R.string.channel_disruptions), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = str(R.string.channel_disruptions_body)
                },
            ),
        )
    }

    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Opens the app (through the app lock, when it's on). */
    fun openApp(context: Context, request: Int = 0): PendingIntent = PendingIntent.getActivity(
        context,
        request,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
