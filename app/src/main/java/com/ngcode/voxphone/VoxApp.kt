package com.ngcode.voxphone

import android.app.Application
import com.voximplant.android.sdk.calls.VICalls
import com.voximplant.android.sdk.core.VICore

class VoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Deliver all SDK events on the main thread so UI and state can be touched directly.
        VICore.callbackExecutor = mainExecutor
        VICore.initialize(this)
        VICalls.initialize()

        CallNotifications.createChannels(this)
        Phone.init(this)
    }
}
