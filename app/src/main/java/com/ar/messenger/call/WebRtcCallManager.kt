package com.ar.messenger.call

import android.content.Context
import com.ar.messenger.data.repo.CallRepository
import org.webrtc.*

/**
 * Wraps a single WebRTC PeerConnection for one AR call.
 * Signaling (offer/answer/ICE) travels through Firestore via CallRepository.
 */
class WebRtcCallManager(
    private val context: Context,
    private val callRepo: CallRepository,
    private val callId: String,
    private val myUid: String
) {
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private val eglBase: EglBase = EglBase.create()
    var localVideoTrack: VideoTrack? = null
    var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSender: RtpSender? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        // Add a TURN server here for reliable calls across mobile-data NATs:
        // PeerConnection.IceServer.builder("turn:YOUR_TURN_HOST:3478")
        //     .setUsername("user").setPassword("pass").createIceServer()
    )

    fun init(isVideoCall: Boolean, onRemoteVideo: (VideoTrack) -> Unit, onLocalVideo: (VideoTrack) -> Unit) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        // enableCodecHwAcceleration=true, plus H264 high-profile enabled for sharper video.
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                callRepo.addIceCandidate(
                    callId, myUid, mapOf(
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "candidate" to candidate.sdp
                    )
                )
            }

            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                val track = receiver.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    onRemoteVideo(track)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
        })

        // Local audio — echo cancellation & noise suppression on for clearer calls.
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        val audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("AR_AUDIO", audioSource)
        peerConnection?.addTrack(localAudioTrack)

        // Local video (only for video calls) — 720p @ 30fps for a noticeably sharper picture
        // than the old 480p/24fps default, with a bumped max bitrate applied after connecting.
        if (isVideoCall) {
            val videoSource = factory.createVideoSource(false)
            videoCapturer = createCameraCapturer()
            val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoCapturer?.initialize(surfaceHelper, context, videoSource.capturerObserver)
            videoCapturer?.startCapture(1280, 720, 30)
            localVideoTrack = factory.createVideoTrack("AR_VIDEO", videoSource)
            onLocalVideo(localVideoTrack!!)
            videoSender = peerConnection?.addTrack(localVideoTrack)
            applyHdBitrate()
        }
    }

    /** Raises the video sender's max bitrate so 720p actually looks sharp instead of blocky. */
    private fun applyHdBitrate() {
        val sender = videoSender ?: return
        val params = sender.parameters
        if (params.encodings.isNotEmpty()) {
            params.encodings[0].maxBitrateBps = 2_500_000 // ~2.5 Mbps ceiling for 720p
            params.encodings[0].minBitrateBps = 300_000
            sender.parameters = params
        }
    }

    fun getEglContext(): EglBase.Context = eglBase.eglBaseContext

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        // Prefer front camera
        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let { return it }
            }
        }
        for (name in deviceNames) {
            enumerator.createCapturer(name, null)?.let { return it }
        }
        return null
    }

    fun createOffer(onSdpCreated: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpAdapter(), sdp)
                onSdpCreated(sdp)
            }
        }, constraints)
    }

    fun createAnswer(onSdpCreated: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SdpAdapter(), sdp)
                onSdpCreated(sdp)
            }
        }, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(SdpAdapter(), sdp)
    }

    fun addRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun hangUp() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
    }

    /** Minimal SdpObserver adapter so we don't repeat empty overrides everywhere. */
    open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
