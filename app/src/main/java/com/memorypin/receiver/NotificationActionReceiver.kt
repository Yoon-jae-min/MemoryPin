package com.memorypin.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.memorypin.R
import com.memorypin.connection.SourceType
import com.memorypin.data.ItemIcon
import com.memorypin.data.MemoryPinRepository
import com.memorypin.service.MonitorService
import com.memorypin.util.AppSettings
import com.memorypin.util.Notifications

/**
 * 알림 액션 버튼을 처리한다.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CARRYING_TOGETHER -> handleCarryingTogether(context, intent)
            ACTION_TRACK_DEVICE_YES -> handleTrackYes(context, intent)
            ACTION_TRACK_DEVICE_NO -> handleTrackNo(context, intent)
        }
    }

    private fun handleCarryingTogether(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return

        MemoryPinRepository.init(context.applicationContext)
        MemoryPinRepository.setCarryingWhileDisconnected(itemId, true)
        MonitorService.cancelLeftBehindWatch(itemId)

        NotificationManagerCompat.from(context).cancel(itemId.hashCode())
    }

    private fun handleTrackYes(context: Context, intent: Intent) {
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID) ?: return
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: sourceId
        val sourceType = SourceType.fromKey(intent.getStringExtra(EXTRA_SOURCE_TYPE))

        MemoryPinRepository.init(context.applicationContext)
        AppSettings.clearDeclinedForAutoRegister(context, sourceId)
        val icon = ItemIcon.guessFromName(displayName)
        MemoryPinRepository.addItem(sourceType, sourceId, displayName, icon.key)

        Notifications.cancelTrackPromptNotification(context, sourceId)
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.toast_track_started, displayName),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleTrackNo(context: Context, intent: Intent) {
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID) ?: return
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: sourceId
        AppSettings.addDeclinedForAutoRegister(context, sourceId, displayName)
        Notifications.cancelTrackPromptNotification(context, sourceId)
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.toast_track_declined, displayName),
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        const val ACTION_CARRYING_TOGETHER = "com.memorypin.action.CARRYING_TOGETHER"
        const val ACTION_TRACK_DEVICE_YES = "com.memorypin.action.TRACK_DEVICE_YES"
        const val ACTION_TRACK_DEVICE_NO = "com.memorypin.action.TRACK_DEVICE_NO"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_SOURCE_TYPE = "extra_source_type"
        const val EXTRA_SOURCE_ID = "extra_source_id"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
    }
}
