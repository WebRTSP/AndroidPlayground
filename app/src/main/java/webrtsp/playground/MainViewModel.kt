package webrtsp.playground

import android.app.Application
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer
import webrtsp.GoogleWebRTCPeer
import webrtsp.Session
import webrtsp.WsClient

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val eglBase: EglBase = EglBase.create()
    private var localPeer: GoogleWebRTCPeer? = null
    var surfaceViewRenderer: SurfaceViewRenderer? = null

    private val wsClient = WsClient(Looper.myLooper()!!) { scope, nextCSeq, sendRequest, sendResponce ->
        val iceServers = listOf<PeerConnection.IceServer>(
            /*
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                .createIceServer(),
            */
        )

        val localPeer = GoogleWebRTCPeer(
            getApplication<Application>().applicationContext,
            eglBase.eglBaseContext,
            iceServers,
            Looper.myLooper()!!)
        this.localPeer = localPeer

        surfaceViewRenderer?.let { surfaceViewRenderer ->
            localPeer.attachSurfaceViewRenderer(surfaceViewRenderer)
        }

        Session(
            scope,
            nextCSeq,
            { localPeer },
            sendRequest,
            sendResponce)
    }

    fun tryConnect() {
        if(wsClient.state.value != WsClient.State.Disconnected) return

        viewModelScope.launch {
            wsClient.connect("ws://clock.webrtsp.org:5554/")
        }
    }

    fun attachSurfaceViewRenderer(surfaceViewRenderer: SurfaceViewRenderer) {
        if(this.surfaceViewRenderer != null) throw IllegalArgumentException("SurfaceViewRenderer already attached")

        this.surfaceViewRenderer = surfaceViewRenderer

        localPeer?.attachSurfaceViewRenderer(surfaceViewRenderer)
    }
    fun detachSurfaceViewRenderer(surfaceViewRenderer: SurfaceViewRenderer) {
        if(this.surfaceViewRenderer != surfaceViewRenderer) throw IllegalArgumentException("Invalid SurfaceViewRenderer")

        this.surfaceViewRenderer = null

        localPeer?.detachSurfaceViewRenderer(surfaceViewRenderer)
    }
}
