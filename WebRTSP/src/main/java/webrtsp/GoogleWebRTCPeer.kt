package webrtsp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class GoogleWebRTCPeer(
    applicationContext: Context,
    eglContext: EglBase.Context,
    iceServers: List<PeerConnection.IceServer>,
    looper: Looper
): WebRTCPeer {
    companion object {
        const val TAG = "GoogleWebRTCPeer"
        private val peerConnectionFactoryInitialized = AtomicBoolean(false)
    }

    private val handler = Handler(looper)

    private var onIceCandidateCallback: IceCandidateCallback = { _, _ -> }
    private var onEosCallback: EosCallback = {}

    // private val iceCandidatesCache = mutableListOf<IceCandidate>()

    private val observer = object: PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {
            Log.d(TAG, "Signaling state: $state")
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.d(TAG, "Ice Connection state: $state")
        }
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            Log.d(TAG, "Ice Gathering state: $state")
            if(state == PeerConnection.IceGatheringState.COMPLETE) {
                handler.post {
                    if(!closed) {
                        onIceCandidateCallback(0, "a=end-of-candidates")
                    }
                }
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {
        }
        override fun onIceCandidate(iceCandidate: IceCandidate) {
            handler.post {
                if(!closed) {
                    onIceCandidateCallback(iceCandidate.sdpMLineIndex, iceCandidate.sdp)
                }
            }
        }
        override fun onIceCandidatesRemoved(removed: Array<IceCandidate>) {
        }
        override fun onAddStream(mediaStream: MediaStream) {
        }

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            val track = receiver.track()
            if(track?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                handler.post {
                    videoTracks.add(track as VideoTrack)
                    if(!closed && videoTracks.size == 1 && surfaceViewRenderer != null) {
                        track.addSink(surfaceViewRenderer)
                    }
                }
            }
        }

        override fun onRemoveTrack(receiver: RtpReceiver) {
            val track = receiver.track()
            if(track?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                handler.post {
                    videoTracks.remove(track as VideoTrack)
                }
            }
        }

        override fun onRemoveStream(mediaStream: MediaStream) {
        }
        override fun onDataChannel(dataChannel: DataChannel) {
        }
        override fun onRenegotiationNeeded() {
        }
    }

    private var peerConnection: PeerConnection? = null

    init {
        if(peerConnectionFactoryInitialized.compareAndSet(false, true)) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                    .createInitializationOptions())
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        //val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglContext)

        val peerConnectionFactory =
            PeerConnectionFactory.builder()
                .setVideoDecoderFactory(videoDecoderFactory)
                .createPeerConnectionFactory()

        peerConnection =
            peerConnectionFactory.createPeerConnection(rtcConfig, observer)

        /*
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        peerConnection?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        */
    }

    private val closed get() = peerConnection == null

    private val videoTracks = mutableSetOf<VideoTrack>()
    private var surfaceViewRenderer: SurfaceViewRenderer? = null

    override fun onIceCandidate(onIceCandidate: IceCandidateCallback) {
        onIceCandidateCallback = onIceCandidate
    }

    override fun onEos(onEos: EosCallback) {
        onEosCallback = onEos
    }

    override suspend fun setRemoteSdp(sdp: String) {
        val peerConnection = this.peerConnection ?: throw IllegalStateException("Missing peerConnection")

        suspendCoroutine<Unit> { continuation ->
            peerConnection.setRemoteDescription(
                object: SdpObserver {
                    override fun onSetSuccess() {
                        continuation.resume(Unit)
                    }
                    override fun onSetFailure(error: String) {
                        continuation.resumeWithException(Error(error))
                    }
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
        }
    }

    override suspend fun getLocalSdp(): String {
        val peerConnection = this.peerConnection ?: throw IllegalStateException("Missing peerConnection")

        val sessionDescription = suspendCoroutine<SessionDescription> { continuation ->
            peerConnection.createAnswer(
            object: SdpObserver {
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String) {}
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    continuation.resume(sessionDescription)
                }
                override fun onCreateFailure(error: String) {
                    continuation.resumeWithException(Error(error))
                }
            }, MediaConstraints())
        }

        suspendCoroutine<Unit> { continuation ->
            peerConnection.setLocalDescription(object: SdpObserver {
                override fun onSetSuccess() {
                    continuation.resume(Unit)
                }
                override fun onSetFailure(error: String) {
                    continuation.resumeWithException(Error(error))
                }
                override fun onCreateSuccess(sessionDescription: SessionDescription) {}
                override fun onCreateFailure(error: String) {}
            }, sessionDescription)
        }

        /*
        iceCandidatesCache.forEach { iceCandidate ->
            Log.d(TAG, "addIceCandidate \"${iceCandidate.sdp}\"")
            peerConnection.addIceCandidate(iceCandidate)
        }
        iceCandidatesCache.clear()
        */

        return sessionDescription.description
    }

    override suspend fun addIceCandidate(mlineIndex: Int, candidate: String) {
        val peerConnection = this.peerConnection ?: throw IllegalStateException("Missing peerConnection")

        val sdpMid = String() // FIXME?

        Log.d(TAG, "addIceCandidate \"${candidate}\"")
        peerConnection.addIceCandidate(IceCandidate(sdpMid, mlineIndex, candidate))
        /*
        when(peerConnection.signalingState()) {
            PeerConnection.SignalingState.HAVE_REMOTE_OFFER -> {
                iceCandidatesCache.add(IceCandidate(sdpMid, mlineIndex, candidate))
            }
            PeerConnection.SignalingState.STABLE, // FIXME ?
            PeerConnection.SignalingState.HAVE_LOCAL_PRANSWER -> {
                iceCandidatesCache.forEach { iceCandidate ->
                    Log.d(TAG, "addIceCandidate (cached) \"${iceCandidate.sdp}\"")
                    peerConnection.addIceCandidate(iceCandidate)
                }
                iceCandidatesCache.clear()
                Log.d(TAG, "addIceCandidate \"${candidate}\"")
                peerConnection.addIceCandidate(IceCandidate(sdpMid, mlineIndex, candidate))
            }
            PeerConnection.SignalingState.HAVE_LOCAL_OFFER,
            PeerConnection.SignalingState.HAVE_REMOTE_PRANSWER,
            PeerConnection.SignalingState.CLOSED,
            null -> {
                throw IllegalStateException("Illegal peerConnection signalling state")
            }
        }
        */
    }

    fun attachSurfaceViewRenderer(surfaceViewRenderer: SurfaceViewRenderer) {
        if(this.surfaceViewRenderer != null) throw IllegalArgumentException("SurfaceViewRenderer already attached")

        val peerConnection = this.peerConnection ?: return

        videoTracks.firstOrNull()?.apply {
           addSink(surfaceViewRenderer)
        }

        this.surfaceViewRenderer = surfaceViewRenderer
    }

    fun detachSurfaceViewRenderer(surfaceViewRenderer: SurfaceViewRenderer) {
        if(this.surfaceViewRenderer != surfaceViewRenderer) throw IllegalArgumentException("Invalid SurfaceViewRenderer")

        val peerConnection = this.peerConnection ?: return

        videoTracks.forEach { track ->
            track.removeSink(surfaceViewRenderer)
        }

        this.surfaceViewRenderer = null
    }

    override fun close() {
        peerConnection?.dispose()
        peerConnection = null

        handler.removeCallbacksAndMessages(null)
    }
}