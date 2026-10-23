package com.ngcode.voxphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object CallNotifications {
    private const val CHANNEL_INCOMING = "incoming"
    private const val CHANNEL_ONGOING = "ongoing"
    private const val ID_INCOMING = 1
    const val ID_ONGOING = 2

    fun createChannels(context: Context) {
        val incoming = NotificationChannel(CHANNEL_INCOMING, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH).apply {
            // The ringtone is played by Phone itself, so it can loop and stop on answer.
            setSound(null, null)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val ongoing = NotificationChannel(CHANNEL_ONGOING, "Текущий звонок", NotificationManager.IMPORTANCE_LOW)
        manager(context).createNotificationChannels(listOf(incoming, ongoing))
    }

    fun showIncoming(context: Context, from: String) {
        val open = activityIntent(context, null, 10)
        val notification = Notification.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Входящий звонок")
            .setContentText(from)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(Notification.Action.Builder(null, "Отклонить", actionIntent(context, CallActionReceiver.ACTION_HANGUP)).build())
            .addAction(Notification.Action.Builder(null, "Ответить", activityIntent(context, MainActivity.ACTION_ANSWER, 11)).build())
            .build()
        runCatching { manager(context).notify(ID_INCOMING, notification) } // no POST_NOTIFICATIONS permission
    }

    fun cancelIncoming(context: Context) = manager(context).cancel(ID_INCOMING)

    fun ongoing(context: Context, text: String): Notification =
        Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Звонок")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(activityIntent(context, null, 12))
            .addAction(Notification.Action.Builder(null, "Завершить", actionIntent(context, CallActionReceiver.ACTION_HANGUP)).build())
            .build()

    private fun manager(context: Context): NotificationManager = context.getSystemService(NotificationManager::class.java)!!

    private fun activityIntent(context: Context, action: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun actionIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
