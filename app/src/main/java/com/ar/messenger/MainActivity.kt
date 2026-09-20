package com.ar.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ar.messenger.call.CallActivity
import com.ar.messenger.data.model.*
import com.ar.messenger.data.repo.AuthRepository
import com.ar.messenger.data.repo.CallRepository
import com.ar.messenger.data.repo.ChatRepository
import com.ar.messenger.ui.screens.*
import com.ar.messenger.ui.theme.ARTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authRepo = AuthRepository()
    private val chatRepo = ChatRepository()
    private val callRepo = CallRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ARTheme {
                ARNavHost(authRepo, chatRepo, callRepo)
            }
        }
    }
}

@Composable
fun ARNavHost(authRepo: AuthRepository, chatRepo: ChatRepository, callRepo: CallRepository) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val startDest = if (authRepo.currentUid != null) "chats" else "login"

    // Presence: mark online while the app is in the foreground, offline when backgrounded.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val uid = authRepo.currentUid ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> chatRepo.setOnlineStatus(uid, true)
                Lifecycle.Event.ON_STOP -> chatRepo.setOnlineStatus(uid, false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Incoming calls: without a push-notification backend, this is what actually makes the
    // callee's phone ring — it watches Firestore for a new call while the app is open/foreground.
    // This is the previously-missing piece that meant calls never rang on the other device.
    val handledCallIds = remember { mutableSetOf<String>() }
    LaunchedEffect(authRepo.currentUid) {
        val uid = authRepo.currentUid ?: return@LaunchedEffect
        scope.launch {
            callRepo.observeIncomingCalls(uid).collect { session ->
                if (session != null && session.calleeId == uid &&
                    session.state == CallState.RINGING && !handledCallIds.contains(session.callId)
                ) {
                    handledCallIds.add(session.callId)
                    CallActivity.start(
                        activity,
                        callId = session.callId,
                        calleeUid = session.callerId,
                        isVideo = session.type == CallType.VIDEO,
                        isOutgoing = false
                    )
                }
            }
        }
    }

    NavHost(navController = nav, startDestination = startDest) {

        composable("login") {
            LoginScreen(
                loading = loading,
                error = error,
                onContinue = { name ->
                    loading = true; error = null
                    authRepo.signInAnonymously(
                        onSuccess = { uid ->
                            loading = false
                            chatRepo.upsertUser(ArUser(uid = uid, name = name, online = true, about = "Available"))
                            nav.navigate("chats") { popUpTo("login") { inclusive = true } }
                        },
                        onFailed = { e -> loading = false; error = e.message }
                    )
                }
            )
        }

        composable("chats") {
            val myUid = authRepo.currentUid ?: ""
            var chats by remember { mutableStateOf(listOf<ChatThread>()) }
            var usersById by remember { mutableStateOf(mapOf<String, ArUser>()) }
            var myName by remember { mutableStateOf("") }

            LaunchedEffect(myUid) {
                scope.launch { chatRepo.observeChats(myUid).collect { chats = it } }
                scope.launch {
                    chatRepo.observeAllUsers(myUid).collect { list ->
                        usersById = list.associateBy { it.uid }
                    }
                }
                scope.launch { chatRepo.observeUser(myUid).collect { myName = it?.name ?: "" } }
            }

            ChatListScreen(
                chats = chats,
                usersById = usersById,
                myUid = myUid,
                myName = myName,
                onOpenChat = { chat ->
                    val otherUid = chat.participants.firstOrNull { it != myUid } ?: ""
                    nav.navigate("chat/${chat.chatId}/$otherUid")
                },
                onNewChat = { nav.navigate("newchat") },
                onTogglePin = { chat, pin -> chatRepo.togglePinChat(chat.chatId, myUid, pin) },
                onOpenProfile = { nav.navigate("profile") }
            )
        }

        composable("profile") {
            val myUid = authRepo.currentUid ?: ""
            var me by remember { mutableStateOf<ArUser?>(null) }
            LaunchedEffect(myUid) {
                scope.launch { chatRepo.observeUser(myUid).collect { me = it } }
            }
            ProfileScreen(
                currentName = me?.name ?: "",
                currentStatus = me?.about ?: "Available",
                onBack = { nav.popBackStack() },
                onSave = { name, status ->
                    chatRepo.upsertUser(ArUser(uid = myUid, name = name, about = status))
                    nav.popBackStack()
                }
            )
        }

        composable("newchat") {
            val myUid = authRepo.currentUid ?: ""
            var users by remember { mutableStateOf(listOf<ArUser>()) }
            LaunchedEffect(Unit) {
                scope.launch { chatRepo.observeAllUsers(myUid).collect { users = it } }
            }
            NewChatScreen(
                users = users,
                onBack = { nav.popBackStack() },
                onPick = { user ->
                    val chatId = chatRepo.chatIdFor(myUid, user.uid)
                    nav.navigate("chat/$chatId/${user.uid}") {
                        popUpTo("chats")
                    }
                }
            )
        }

        composable("chat/{chatId}/{peerUid}") { backStackEntry ->
            val myUid = authRepo.currentUid ?: ""
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val peerUid = backStackEntry.arguments?.getString("peerUid") ?: ""
            var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
            var peerUser by remember { mutableStateOf<ArUser?>(null) }
            var typingUsers by remember { mutableStateOf(listOf<String>()) }

            LaunchedEffect(chatId) {
                scope.launch {
                    chatRepo.observeMessages(chatId).collect { list ->
                        messages = list
                        chatRepo.markMessagesRead(chatId, myUid)
                    }
                }
                scope.launch { chatRepo.observeUser(peerUid).collect { peerUser = it } }
                scope.launch { chatRepo.observeTypingUsers(chatId).collect { typingUsers = it } }
            }

            ChatScreen(
                peerName = peerUser?.name?.ifBlank { "Unknown" } ?: "",
                peerOnline = peerUser?.online ?: false,
                peerTyping = typingUsers.contains(peerUid),
                myUid = myUid,
                messages = messages,
                onBack = { nav.popBackStack() },
                onSend = { text ->
                    chatRepo.sendMessage(
                        chatId = chatId,
                        participants = listOf(myUid, peerUid),
                        message = ChatMessage(
                            senderId = myUid,
                            text = text,
                            type = MessageType.TEXT,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                },
                onTypingChanged = { isTyping -> chatRepo.setTyping(chatId, myUid, isTyping) },
                onReact = { message, emoji ->
                    chatRepo.toggleReaction(chatId, message.messageId, myUid, emoji, message.reactions[myUid])
                },
                onVoiceCall = {
                    CallActivity.start(activity, callId = "${chatId}_${System.currentTimeMillis()}", calleeUid = peerUid, isVideo = false, isOutgoing = true)
                },
                onVideoCall = {
                    CallActivity.start(activity, callId = "${chatId}_${System.currentTimeMillis()}", calleeUid = peerUid, isVideo = true, isOutgoing = true)
                }
            )
        }
    }
}
