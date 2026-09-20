package com.ar.messenger.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ar.messenger.MainActivity
import com.ar.messenger.call.CallActivity
import com.ar.messenger.data.repo.ChatRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles incoming FCM pushes for:
 *  - "call"   -> launches CallActivity as an incoming call
 *  - "message" -> shows a chat notification
 *
 * Server-side sending (Cloud Function / relay) must set data payload:
 *  { type: "call", callId, callerUid, isVideo } or
 *  { type: "message", chatId, senderName, text }
 */
class ARMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = com.ar.messenger.data.repo.AuthRepository().currentUid ?: return
        ChatRepository().upsertUser(com.ar.messenger.data.model.ArUser(uid = uid, fcmToken = token))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "call" -> {
                val callId = data["callId"] ?: return
                val callerUid = data["callerUid"] ?: return
                val isVideo = data["isVideo"] == "true"
                CallActivity.start(this, callId, callerUid, isVideo, isOutgoing = false)
            }
            "message" -> showChatNotification(
                title = data["senderName"] ?: "AR",
                body = data["text"] ?: "New message"
            )
        }
    }

    private fun showChatNotification(title: String, body: String) {
        val channelId = "ar_messages_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "AR Messages", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        getSystemService(NotificationManager::class.java).notify(System.currentTimeMillis().toInt(), notification)
    }
}
