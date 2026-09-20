package com.ar.messenger.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import com.ar.messenger.data.model.CallState
import com.ar.messenger.data.model.CallType
import com.ar.messenger.data.repo.AuthRepository
import com.ar.messenger.data.repo.CallRepository
import com.ar.messenger.data.repo.ChatRepository
import com.ar.messenger.ui.theme.ARTheme
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

class CallActivity : ComponentActivity() {

    private val callRepo = CallRepository()
    private val authRepo = AuthRepository()
    private val chatRepo = ChatRepository()
    private var rtcManager: WebRtcCallManager? = null
    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_PEER = "peerUid" // the OTHER party's uid, for both directions
        const val EXTRA_VIDEO = "isVideo"
        const val EXTRA_OUTGOING = "isOutgoing"

        fun start(context: Context, callId: String, calleeUid: String, isVideo: Boolean, isOutgoing: Boolean) {
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_PEER, calleeUid)
                putExtra(EXTRA_VIDEO, isVideo)
                putExtra(EXTRA_OUTGOING, isOutgoing)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun startRinging() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.play()
        } catch (_: Exception) {}

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 700, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return finish()
        val peerUid = intent.getStringExtra(EXTRA_PEER) ?: ""
        val isVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
        val isOutgoing = intent.getBooleanExtra(EXTRA_OUTGOING, true)
        val myUid = authRepo.currentUid ?: return finish()

        val manager = WebRtcCallManager(this, callRepo, callId, myUid)
        rtcManager = manager

        setContent {
            ARTheme {
                var remoteTrack by remember { mutableStateOf<VideoTrack?>(null) }
                var localTrack by remember { mutableStateOf<VideoTrack?>(null) }
                var callState by remember { mutableStateOf(CallState.RINGING) }
                var accepted by remember { mutableStateOf(isOutgoing) } // caller is "accepted" from their own side
                var offerSdp by remember { mutableStateOf("") }
                var answered by remember { mutableStateOf(false) }
                var mediaReady by remember { mutableStateOf(false) }
                var hasPermissions by remember { mutableStateOf(false) }
                var peerName by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()

                // --- Runtime permissions: this was missing before, which silently broke calls ---
                val permissionLauncher = rememberLauncherForCallPermissions { granted ->
                    hasPermissions = granted
                }
                LaunchedEffect(Unit) {
                    val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (isVideo) needed.add(Manifest.permission.CAMERA)
                    val allGranted = needed.all {
                        ContextCompat.checkSelfPermission(this@CallActivity, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (allGranted) hasPermissions = true else permissionLauncher.launch(needed.toTypedArray())
                }

                LaunchedEffect(Unit) {
                    scope.launch { chatRepo.observeUser(peerUid).collect { peerName = it?.name ?: "" } }
                }

                // Start ringtone/vibration only for an incoming call still awaiting Accept/Decline.
                LaunchedEffect(accepted, isOutgoing) {
                    if (!isOutgoing && !accepted) startRinging() else stopRinging()
                }

                // Once permissions are granted, initialize WebRTC and (if outgoing) create the offer.
                LaunchedEffect(hasPermissions) {
                    if (!hasPermissions) return@LaunchedEffect
                    manager.init(
                        isVideoCall = isVideo,
                        onRemoteVideo = { remoteTrack = it },
                        onLocalVideo = { localTrack = it }
                    )
                    mediaReady = true

                    if (isOutgoing) {
                        manager.createOffer { sdp ->
                            callRepo.createCall(
                                com.ar.messenger.data.model.CallSession(
                                    callId = callId, callerId = myUid, calleeId = peerUid,
                                    type = if (isVideo) CallType.VIDEO else CallType.AUDIO,
                                    state = CallState.RINGING, startTime = System.currentTimeMillis(),
                                    offerSdp = sdp.description
                                )
                            )
                        }
                    }

                    scope.launch {
                        callRepo.observeCall(callId).collect { session ->
                            if (session == null) return@collect
                            callState = session.state
                            if (isOutgoing && session.answerSdp.isNotBlank()) {
                                manager.setRemoteDescription(
                                    SessionDescription(SessionDescription.Type.ANSWER, session.answerSdp)
                                )
                            }
                            if (!isOutgoing && session.offerSdp.isNotBlank()) {
                                offerSdp = session.offerSdp
                            }
                            if (session.state == CallState.ENDED || session.state == CallState.DECLINED) {
                                stopRinging()
                                finish()
                            }
                        }
                    }

                    scope.launch {
                        callRepo.observeIceCandidates(callId, peerUid).collect { data ->
                            val mid = data["sdpMid"] as? String
                            val idx = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                            val cand = data["candidate"] as? String ?: return@collect
                            manager.addRemoteIceCandidate(mid, idx, cand)
                        }
                    }
                }

                // Only build & send the answer once the callee has tapped Accept.
                LaunchedEffect(accepted, offerSdp, mediaReady) {
                    if (!isOutgoing && accepted && offerSdp.isNotBlank() && mediaReady && !answered) {
                        answered = true
                        stopRinging()
                        manager.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offerSdp))
                        manager.createAnswer { sdp -> callRepo.setAnswer(callId, sdp.description) }
                        callRepo.updateCallState(callId, CallState.ACCEPTED)
                    }
                }

                CallScreen(
                    isVideo = isVideo,
                    isOutgoing = isOutgoing,
                    accepted = accepted,
                    callState = callState,
                    peerName = peerName.ifBlank { "AR user" },
                    eglContext = manager.getEglContext(),
                    localTrack = localTrack,
                    remoteTrack = remoteTrack,
                    onMute = { muted -> manager.toggleMute(muted) },
                    onSwitchCamera = { manager.switchCamera() },
                    onAccept = { accepted = true },
                    onDecline = {
                        stopRinging()
                        callRepo.updateCallState(callId, CallState.DECLINED)
                        finish()
                    },
                    onHangUp = {
                        stopRinging()
                        callRepo.updateCallState(callId, CallState.ENDED)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
        rtcManager?.hangUp()
    }
}

@Composable
private fun rememberLauncherForCallPermissions(onResult: (Boolean) -> Unit) =
    androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> onResult(result.values.all { it }) }
