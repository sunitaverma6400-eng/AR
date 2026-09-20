package com.ar.messenger.call

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ar.messenger.data.model.CallState
import com.ar.messenger.ui.components.GradientAvatar
import com.ar.messenger.ui.theme.ArHeroGradient
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    isVideo: Boolean,
    isOutgoing: Boolean,
    accepted: Boolean,
    callState: CallState,
    peerName: String,
    eglContext: EglBase.Context,
    localTrack: VideoTrack?,
    remoteTrack: VideoTrack?,
    onMute: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit
) {
    var muted by remember { mutableStateOf(false) }
    val showIncomingPrompt = !isOutgoing && !accepted && callState == CallState.RINGING
    val isConnected = accepted && remoteTrack != null || (accepted && !isVideo && callState == CallState.ACCEPTED)

    Box(modifier = Modifier.fillMaxSize().background(ArHeroGradient)) {

        if (isVideo && remoteTrack != null) {
            RemoteVideoView(eglContext, remoteTrack, Modifier.fillMaxSize())
        }

        if (isVideo && localTrack != null && (accepted)) {
            LocalVideoPreview(
                eglContext, localTrack,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(110.dp, 150.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            if (!isVideo || remoteTrack == null) {
                GradientAvatar(name = peerName, size = 110.dp)
                Spacer(Modifier.height(18.dp))
            }

            Text(peerName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    showIncomingPrompt -> if (isVideo) "Incoming video call…" else "Incoming call…"
                    !accepted && isOutgoing -> "Calling…"
                    callState == CallState.ACCEPTED -> "Connected"
                    callState == CallState.DECLINED -> "Declined"
                    callState == CallState.ENDED -> "Call ended"
                    else -> "Ringing…"
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp
            )

            Spacer(Modifier.weight(1f))

            if (showIncomingPrompt) {
                // Incoming call: Accept / Decline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CallControlButton(icon = Icons.Default.CallEnd, background = Color(0xFFE0435A), size = 68.dp, onClick = onDecline)
                        Spacer(Modifier.height(6.dp))
                        Text("Decline", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CallControlButton(icon = Icons.Default.Call, background = Color(0xFF4CE0B3), size = 68.dp, onClick = onAccept)
                        Spacer(Modifier.height(6.dp))
                        Text("Accept", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            } else {
                // Outgoing/ringing or in-call: normal controls
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (accepted) {
                        CallControlButton(
                            icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                            background = Color.White.copy(alpha = 0.15f)
                        ) {
                            muted = !muted
                            onMute(muted)
                        }
                        if (isVideo) {
                            CallControlButton(
                                icon = Icons.Default.Cameraswitch,
                                background = Color.White.copy(alpha = 0.15f),
                                onClick = onSwitchCamera
                            )
                        }
                    }
                    CallControlButton(
                        icon = Icons.Default.CallEnd,
                        background = Color(0xFFE0435A),
                        onClick = onHangUp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    size: androidx.compose.ui.unit.Dp = 60.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size / 2.2f))
        }
    }
}

@Composable
private fun RemoteVideoView(eglContext: EglBase.Context, track: VideoTrack, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                track.addSink(this)
            }
        }
    )
}

@Composable
private fun LocalVideoPreview(eglContext: EglBase.Context, track: VideoTrack, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(true)
                track.addSink(this)
            }
        }
    )
}
