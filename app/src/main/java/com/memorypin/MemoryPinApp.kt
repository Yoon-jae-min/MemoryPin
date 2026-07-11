package com.memorypin

import android.app.Application
import com.memorypin.data.MemoryPinRepository
import com.memorypin.util.Notifications

class MemoryPinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MemoryPinRepository.init(this)
        Notifications.ensureChannels(this)
    }
}
