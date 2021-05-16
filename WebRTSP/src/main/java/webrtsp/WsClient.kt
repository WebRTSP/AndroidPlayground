package webrtsp

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

typealias NextCSeq = () -> CSeq
typealias SendRequest = suspend (request: Request) -> Response
typealias SendResponse = (response: Response) -> Unit

class WsClient(
    private val looper: Looper,
    private val createSession: (
        scope: CoroutineScope,
        nextCSeq: NextCSeq,
        sendRequest: SendRequest,
        sendResponse: SendResponse,
    ) -> Session
) {
    companion object {
        const val TAG = "WsClient"
    }

    enum class State {
        Disconnected,
        Connecting,
        Connected
    }

    private val handler = Handler(looper)
    private val dispatcher = handler.asCoroutineDispatcher().immediate
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val httpClient = OkHttpClient()
    private var websocket: WebSocket? = null

    private var cSeq: CSeq = 0

    private val sentRequests = HashMap<CSeq, Continuation<Response>>()

    private val mutableState = MutableStateFlow(State.Disconnected)
    val state: StateFlow<State> = mutableState

    private var session: Session? = null

    private val wsEventsListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            mutableState.value = State.Connected
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "-> WsClient: $text")
            handler.post {
                onMessage(text)
            }
        }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closed")
            mutableState.value = State.Disconnected
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            Log.d(TAG, "Failured with $t")
            mutableState.value = State.Disconnected
        }
    }

    private fun nextCSeq(): CSeq {
        do ++cSeq while(sentRequests.contains(cSeq))
        return cSeq
    }

    suspend fun connect(url: String) {
        scope.launch {
            if(state.value != State.Disconnected) return@launch

            mutableState.value = State.Connecting

            val wsRequest = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Sec-WebSocket-Protocol", "webrtsp")
                .build()
            websocket = httpClient.newWebSocket(wsRequest, wsEventsListener)

            val connected = state.first { state -> state != State.Connecting } == State.Connected

            if(connected) {
                val session = createSession(scope,
                    { nextCSeq() },
                    { request ->
                        send(request)
                    },
                    { response ->
                        send(response)
                    })
                this@WsClient.session = session

                session.onConnected()
            }
        }.join()
    }

    private suspend fun send(request: Request): Response = withContext(dispatcher) {
        if(sentRequests.containsKey(request.cSeq))
            throw IllegalArgumentException()

        val websocket = this@WsClient.websocket ?: throw IllegalStateException()

        val message = Serializer.Serialize(request)
        Log.d(TAG, "WsClient -> : $message")
        websocket.send(message)

        return@withContext suspendCoroutine<Response> { continuation ->
            sentRequests[request.cSeq] = continuation
        }
    }
    private fun send(response: Response) {
        val websocket = this@WsClient.websocket ?: throw IllegalStateException()

        val message = Serializer.Serialize(response)
        Log.d(TAG, "WsClient -> : $message")
        websocket.send(message)
    }
    private fun onMessage(message: String) {
        val session = this.session ?: return

        if(Parser.IsRequest(message)) {
            val request = Parser.ParseRequest(message)
            assert(request != null) // FIXME!
            if(request != null)
                session.onRequest(request)
        } else {
            val response = Parser.ParseResponse(message)
            assert(response != null) // FIXME!
            if(response != null) {
                sentRequests.remove(response.cSeq)?.apply {
                    resume(response)
                } ?: assert(false) // FIXME!
            }
        }
    }
}
