package com.ngcode.voxphone

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground service kept alive for the duration of a call, so the call (and the microphone)
 * keeps working while the app is in the background or the screen is off.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = Phone.call?.let { Phone.peerName(it) } ?: ""
        val notification = CallNotifications.ongoing(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                CallNotifications.ID_ONGOING, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(CallNotifications.ID_ONGOING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(CallNotifications.ID_ONGOING, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context) {
            // May be refused when started from the background; the call still works while the app is open.
            runCatching { context.startForegroundService(Intent(context, CallService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
