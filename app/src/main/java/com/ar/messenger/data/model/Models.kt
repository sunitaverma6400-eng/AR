package com.ar.messenger.data.model

data class ArUser(
    val uid: String = "",
    val phone: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val about: String = "Available",
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val fcmToken: String = ""
)

data class ChatThread(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastSenderId: String = "",
    val unreadCount: Map<String, Int> = emptyMap(),
    val pinnedBy: List<String> = emptyList(),
    val typingUsers: List<String> = emptyList()
)

enum class MessageType { TEXT, IMAGE, AUDIO, CALL_LOG }
enum class MessageStatus { SENT, DELIVERED, READ }

data class ChatMessage(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = 0L,
    val status: MessageStatus = MessageStatus.SENT,
    val reactions: Map<String, String> = emptyMap(),
    val replyToText: String = "",
    val replyToSenderId: String = ""
)

enum class CallType { AUDIO, VIDEO }
enum class CallState { RINGING, ACCEPTED, DECLINED, ENDED, MISSED }

data class CallSession(
    val callId: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    val type: CallType = CallType.AUDIO,
    val state: CallState = CallState.RINGING,
    val startTime: Long = 0L,
    val offerSdp: String = "",
    val answerSdp: String = ""
)
