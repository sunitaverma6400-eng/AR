package com.ar.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ar.messenger.data.model.ArUser
import com.ar.messenger.data.model.ChatThread
import com.ar.messenger.ui.components.GradientAvatar
import com.ar.messenger.ui.theme.ArAccent
import com.ar.messenger.ui.theme.ArHeroGradient
import com.ar.messenger.ui.theme.ArPrimaryGradient
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    chats: List<ChatThread>,
    usersById: Map<String, ArUser>,
    myUid: String,
    myName: String,
    onOpenChat: (ChatThread) -> Unit,
    onNewChat: () -> Unit,
    onTogglePin: (ChatThread, Boolean) -> Unit,
    onOpenProfile: () -> Unit
) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("AR", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    Box(Modifier.padding(start = 12.dp)) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.padding(4.dp)
                        ) {
                            androidx.compose.material3.IconButton(onClick = onOpenProfile) {
                                GradientAvatar(name = myName, size = 32.dp)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { }) { Icon(Icons.Default.Videocam, contentDescription = "Video") }
                    IconButton(onClick = { }) { Icon(Icons.Default.Call, contentDescription = "Calls") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ArPrimaryGradient)
            ) {
                Icon(Icons.Default.Chat, contentDescription = "New chat", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ArHeroGradient)
                .padding(padding)
        ) {
            if (chats.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Koi chat nahi hai abhi.\nNaya chat shuru karo →",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(chats, key = { it.chatId }) { chat ->
                        val otherUid = chat.participants.firstOrNull { it != myUid } ?: ""
                        val otherUser = usersById[otherUid]
                        val isPinned = chat.pinnedBy.contains(myUid)
                        val isTypingHere = chat.typingUsers.contains(otherUid)
                        ChatRow(
                            name = otherUser?.name?.ifBlank { "Unknown" } ?: "Unknown",
                            lastMessage = if (isTypingHere) "typing…" else chat.lastMessage,
                            time = formatTime(chat.lastMessageTime),
                            unread = chat.unreadCount[myUid] ?: 0,
                            online = otherUser?.online ?: false,
                            pinned = isPinned,
                            isTyping = isTypingHere,
                            onClick = { onOpenChat(chat) },
                            onTogglePin = { onTogglePin(chat, !isPinned) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    name: String,
    lastMessage: String,
    time: String,
    unread: Int,
    online: Boolean,
    pinned: Boolean,
    isTyping: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onTogglePin)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientAvatar(name = name, size = 50.dp, showOnlineDot = true, isOnline = online)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) {
                    Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = ArAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(
                lastMessage,
                fontSize = 13.sp,
                color = if (isTyping) ArAccent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontStyle = if (isTyping) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            if (unread > 0) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ArPrimaryGradient)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(unread.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis == 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}
