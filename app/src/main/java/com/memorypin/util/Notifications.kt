package com.memorypin.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.memorypin.connection.SourceType
import com.memorypin.R
import com.memorypin.data.TrackedItem
import com.memorypin.receiver.NotificationActionReceiver
import com.memorypin.ui.MainActivity
import com.memorypin.ui.SpotDetailActivity

object Notifications {

    const val CHANNEL_SERVICE = "memorypin_service"
    const val CHANNEL_SAVED = "memorypin_saved"
    const val CHANNEL_ALERT = "memorypin_alert"
    const val CHANNEL_PROMPT = "memorypin_prompt"
    const val SERVICE_NOTIF_ID = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notif_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_service_desc)
            setShowBadge(false)
        }

        val saved = NotificationChannel(
            CHANNEL_SAVED,
            context.getString(R.string.notif_channel_saved),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_saved_desc)
        }

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.notif_channel_alert),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_alert_desc)
        }

        val prompt = NotificationChannel(
            CHANNEL_PROMPT,
            context.getString(R.string.notif_channel_prompt),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_prompt_desc)
        }

        manager.createNotificationChannel(service)
        manager.createNotificationChannel(saved)
        manager.createNotificationChannel(alert)
        manager.createNotificationChannel(prompt)
    }

    fun buildServiceNotification(context: Context): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            pendingFlags()
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(context.getString(R.string.notif_service_title))
            .setContentText(context.getString(R.string.notif_service_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showSavedNotification(context: Context, item: TrackedItem) {
        ensureChannels(context)

        val detail = Intent(context, SpotDetailActivity::class.java).apply {
            putExtra(SpotDetailActivity.EXTRA_ITEM_ID, item.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            detail,
            pendingFlags()
        )

        val carryingIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CARRYING_TOGETHER
            putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, item.id)
        }
        val carryingPending = PendingIntent.getBroadcast(
            context,
            ("carry_" + item.id).hashCode(),
            carryingIntent,
            pendingFlags()
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SAVED)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(context.getString(R.string.notif_saved_title))
            .setContentText(context.getString(R.string.notif_saved_text, item.displayName))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (item.alertEnabled) {
            builder.addAction(
                R.drawable.ic_location,
                context.getString(R.string.notif_action_carrying),
                carryingPending
            )
        }

        val notif = builder.build()

        if (canNotify(context)) {
            NotificationManagerCompat.from(context).notify(item.id.hashCode(), notif)
        }
    }

    /** "두고 오셨나요?" 알림. (끊김 + 멀어짐) */
    fun showLeftBehindNotification(context: Context, item: TrackedItem) {
        ensureChannels(context)

        val detail = Intent(context, SpotDetailActivity::class.java).apply {
            putExtra(SpotDetailActivity.EXTRA_ITEM_ID, item.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            ("alert_" + item.id).hashCode(),
            detail,
            pendingFlags()
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(context.getString(R.string.notif_left_behind_title))
            .setContentText(context.getString(R.string.notif_left_behind_text, item.displayName))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (canNotify(context)) {
            NotificationManagerCompat.from(context).notify(("alert_" + item.id).hashCode(), notif)
        }
    }

    /** 페어링된 기기가 처음 연결될 때 추적 여부를 묻는다. */
    fun showTrackPromptNotification(
        context: Context,
        sourceType: SourceType,
        sourceId: String,
        displayName: String
    ) {
        ensureChannels(context)

        val notifId = trackPromptNotifId(sourceId)

        val trackYesIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TRACK_DEVICE_YES
            putExtra(NotificationActionReceiver.EXTRA_SOURCE_TYPE, sourceType.key)
            putExtra(NotificationActionReceiver.EXTRA_SOURCE_ID, sourceId)
            putExtra(NotificationActionReceiver.EXTRA_DISPLAY_NAME, displayName)
        }
        val trackYesPending = PendingIntent.getBroadcast(
            context,
            ("track_yes_" + sourceId).hashCode(),
            trackYesIntent,
            pendingFlags()
        )

        val trackNoIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TRACK_DEVICE_NO
            putExtra(NotificationActionReceiver.EXTRA_SOURCE_ID, sourceId)
            putExtra(NotificationActionReceiver.EXTRA_DISPLAY_NAME, displayName)
        }
        val trackNoPending = PendingIntent.getBroadcast(
            context,
            ("track_no_" + sourceId).hashCode(),
            trackNoIntent,
            pendingFlags()
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_PROMPT)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(context.getString(R.string.notif_track_prompt_title))
            .setContentText(context.getString(R.string.notif_track_prompt_text, displayName))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_location,
                context.getString(R.string.notif_action_track_yes),
                trackYesPending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notif_action_track_no),
                trackNoPending
            )
            .build()

        if (canNotify(context)) {
            NotificationManagerCompat.from(context).notify(notifId, notif)
        }
    }

    fun cancelTrackPromptNotification(context: Context, sourceId: String) {
        NotificationManagerCompat.from(context).cancel(trackPromptNotifId(sourceId))
    }

    private fun trackPromptNotifId(sourceId: String): Int = ("track_prompt_" + sourceId).hashCode()

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun pendingFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }
}
