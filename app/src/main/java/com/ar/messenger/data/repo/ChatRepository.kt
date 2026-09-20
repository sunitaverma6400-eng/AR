package com.ar.messenger.data.repo

import com.ar.messenger.data.model.ArUser
import com.ar.messenger.data.model.ChatMessage
import com.ar.messenger.data.model.ChatThread
import com.ar.messenger.data.model.MessageStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ChatRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCol = db.collection("users")
    private val chatsCol = db.collection("chats")

    fun upsertUser(user: ArUser) {
        usersCol.document(user.uid).set(user, SetOptions.merge())
    }

    fun setOnlineStatus(uid: String, online: Boolean) {
        usersCol.document(uid).set(
            mapOf("online" to online, "lastSeen" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    fun observeUser(uid: String): Flow<ArUser?> = callbackFlow {
        val reg = usersCol.document(uid).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject(ArUser::class.java))
        }
        awaitClose { reg.remove() }
    }

    fun observeAllUsers(excludeUid: String): Flow<List<ArUser>> = callbackFlow {
        val reg = usersCol.addSnapshotListener { snap, _ ->
            val list = snap?.documents
                ?.mapNotNull { it.toObject(ArUser::class.java) }
                ?.filter { it.uid != excludeUid }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    fun chatIdFor(uidA: String, uidB: String): String =
        listOf(uidA, uidB).sorted().joinToString("_")

    fun observeChats(myUid: String): Flow<List<ChatThread>> = callbackFlow {
        val reg = chatsCol
            .whereArrayContains("participants", myUid)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject(ChatThread::class.java) }
                    ?.sortedWith(
                        compareByDescending<ChatThread> { it.pinnedBy.contains(myUid) }
                            .thenByDescending { it.lastMessageTime }
                    )
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = chatsCol.document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun sendMessage(chatId: String, participants: List<String>, message: ChatMessage) {
        val chatRef = chatsCol.document(chatId)
        val msgRef = chatRef.collection("messages").document()
        val toSave = message.copy(messageId = msgRef.id, chatId = chatId)
        msgRef.set(toSave)

        // Merge so we never clobber pinnedBy / typingUsers set elsewhere on this doc.
        chatRef.set(
            mapOf(
                "chatId" to chatId,
                "participants" to participants,
                "lastMessage" to message.text.ifBlank { "[media]" },
                "lastMessageTime" to message.timestamp,
                "lastSenderId" to message.senderId
            ),
            SetOptions.merge()
        )
    }

    /** Live "typing…" indicator — adds/removes this user's uid from the chat doc. */
    fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        val chatRef = chatsCol.document(chatId)
        val update = if (isTyping) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
        chatRef.set(mapOf("typingUsers" to update), SetOptions.merge())
    }

    fun observeTypingUsers(chatId: String): Flow<List<String>> = callbackFlow {
        val reg = chatsCol.document(chatId).addSnapshotListener { snap, _ ->
            val thread = snap?.toObject(ChatThread::class.java)
            trySend(thread?.typingUsers ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    /** Pin/unpin a chat to the top of the list — per-user, so pinning doesn't affect the other side. */
    fun togglePinChat(chatId: String, myUid: String, pin: Boolean) {
        val chatRef = chatsCol.document(chatId)
        val update = if (pin) FieldValue.arrayUnion(myUid) else FieldValue.arrayRemove(myUid)
        chatRef.set(mapOf("pinnedBy" to update), SetOptions.merge())
    }

    /** Toggle an emoji reaction from this user on a message — tapping the same emoji again removes it. */
    fun toggleReaction(chatId: String, messageId: String, uid: String, emoji: String, currentlySet: String?) {
        val msgRef = chatsCol.document(chatId).collection("messages").document(messageId)
        if (currentlySet == emoji) {
            msgRef.update("reactions.$uid", FieldValue.delete())
        } else {
            msgRef.update("reactions.$uid", emoji)
        }
    }

    /** Marks every message from the other person as READ once this user opens the chat. */
    fun markMessagesRead(chatId: String, myUid: String) {
        chatsCol.document(chatId).collection("messages")
            .whereNotEqualTo("senderId", myUid)
            .get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                snap.documents.forEach { doc ->
                    val status = doc.getString("status")
                    if (status != MessageStatus.READ.name) {
                        batch.update(doc.reference, "status", MessageStatus.READ.name)
                    }
                }
                batch.commit()
            }
    }
}
