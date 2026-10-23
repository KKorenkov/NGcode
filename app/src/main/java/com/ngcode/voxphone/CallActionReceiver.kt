package com.ngcode.voxphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles "Reject" / "Hang up" buttons in notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_HANGUP) Phone.hangup()
    }

    companion object {
        const val ACTION_HANGUP = "com.ngcode.voxphone.HANGUP"
    }
}
