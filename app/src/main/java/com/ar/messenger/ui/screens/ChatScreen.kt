package com.ar.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ar.messenger.data.model.ChatMessage
import com.ar.messenger.data.model.MessageStatus
import com.ar.messenger.ui.components.GradientAvatar
import com.ar.messenger.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val quickEmojis = listOf("❤️", "😂", "😮", "😢", "👍", "🔥")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerName: String,
    peerOnline: Boolean,
    peerTyping: Boolean,
    myUid: String,
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onReact: (ChatMessage, String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var reactionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientAvatar(name = peerName, size = 34.dp, showOnlineDot = true, isOnline = peerOnline)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(peerName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                when {
                                    peerTyping -> "typing…"
                                    peerOnline -> "Online"
                                    else -> "Offline"
                                },
                                fontSize = 11.sp,
                                color = if (peerTyping) ArAccent else if (peerOnline) ArOnline else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onVideoCall) { Icon(Icons.Default.Videocam, contentDescription = "Video call") }
                    IconButton(onClick = onVoiceCall) { Icon(Icons.Default.Call, contentDescription = "Voice call") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        val wasBlank = input.isBlank()
                        input = it
                        if (it.isNotBlank() && wasBlank) onTypingChanged(true)
                        if (it.isBlank()) onTypingChanged(false)
                    },
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (input.isNotBlank()) ArPrimaryGradient else Brush.linearGradient(listOf(ArSurface, ArSurface)))
                ) {
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                onSend(input.trim())
                                input = ""
                                onTypingChanged(false)
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ArHeroGradient)
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.messageId.ifBlank { it.timestamp.toString() } }) { msg ->
                    MessageBubble(
                        message = msg,
                        isMine = msg.senderId == myUid,
                        onLongPress = { reactionTarget = msg }
                    )
                }
            }

            reactionTarget?.let { target ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { reactionTarget = null },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(ArSurface)
                            .padding(16.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            quickEmojis.forEach { emoji ->
                                Text(
                                    emoji,
                                    fontSize = 26.sp,
                                    modifier = Modifier.clickable {
                                        onReact(target, emoji)
                                        reactionTarget = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isMine: Boolean, onLongPress: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = if (isMine) ArPrimaryGradient else Brush.linearGradient(listOf(ArBubbleIn, ArBubbleIn)),
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp
                    )
                )
                .pointerInput(message.messageId) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 280.dp)
        ) {
            Column {
                Text(message.text, fontSize = 15.sp, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    if (isMine) {
                        Spacer(Modifier.width(4.dp))
                        val icon = if (message.status == MessageStatus.READ) Icons.Default.DoneAll else Icons.Default.Done
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (message.status == MessageStatus.READ) ArReadTick else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        if (message.reactions.isNotEmpty()) {
            val emojiCounts = message.reactions.values.groupingBy { it }.eachCount()
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ArSurface)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                emojiCounts.forEach { (emoji, count) ->
                    Text("$emoji${if (count > 1) " $count" else ""}", fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}
