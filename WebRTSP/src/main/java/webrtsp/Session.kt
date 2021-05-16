package webrtsp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.IllegalStateException

typealias CreatePeer = () -> WebRTCPeer

open class Session(
    private val scope: CoroutineScope,
    private val nextCSeq: NextCSeq,
    private val createPeer: CreatePeer,
    private val sendRequest: SendRequest,
    private val sendResponse: SendResponse,
) {
    private var supportedMethods = setOf<Method>()
    private var list = mapOf<String, String>()
    private var sessionId: SessionId? = null
    private var uri: String? = null
    private var localPeer: WebRTCPeer? = null

    fun onRequest(request: Request) {
        when(request.method) {
            Method.SETUP -> onSetupRequest(request)
            Method.TEARDOWN ->  onTeardownRequest(request)
            Method.OPTIONS,
            Method.LIST,
            Method.DESCRIBE,
            Method.PLAY,
            Method.RECORD,
            Method.GET_PARAMETER,
            Method.SET_PARAMETER -> assert(false) // FIXME!
        }
    }

    private fun isSupported(method: Method): Boolean {
        return supportedMethods.contains(method)
    }

    private fun sendOkResponse(cSeq: CSeq, sessionId: SessionId) {
        sendResponse(Response(Protocol.Current, KnownStatus.Ok.code, "OK", cSeq, sessionId))
    }

    protected open fun onSetupRequest(request: Request) {
        val localPeer = this.localPeer ?: throw Error("Missing local peer in SETUP request")

        val sessionId = request.sessionId ?: run {
            throw Error("Missing Session in SETUP request")
        }
        if(sessionId != this.sessionId) {
            throw Error("Unknown Session in SETUP request")
        }
        if(request.contentType != "application/x-ice-candidate") {
            throw Error("Invalid \"content-type\" = \"${request.contentType}\" in SETUP request")
        }
        val iceCandidates = Parser.ParseIceCandidates(request.body) ?: run {
            throw Error("Fail parse Ice Candidates")
        }

        iceCandidates.forEach { pair ->
            scope.launch {
                localPeer.addIceCandidate(pair.first, pair.second)
            }
        }

        sendOkResponse(request.cSeq, request.sessionId)
    }
    protected open fun onTeardownRequest(request: Request) {
        val sessionId = request.sessionId ?: run {
            throw Error("Missing Session in TEARDOWN request")
        }
        if(sessionId != this.sessionId) {
            throw Error("Unknown Session in TEARDOWN request")
        }

        localPeer?.close()
        localPeer = null
        this.sessionId = null

        sendOkResponse(request.cSeq, request.sessionId)
    }

    private suspend fun requestOptions(uri: String) {
        val response = sendRequest(Request(Method.OPTIONS, uri, Protocol.Current, nextCSeq()))

        supportedMethods = setOf<Method>()

        val public = response.headerFields["public"] ?: run {
            throw Error("Missing \"Public\" header in OPTIONS response")
        }
        val methods = Parser.ParseOptions(public) ?: run {
            throw Error("Fail parse Options")
        }

        supportedMethods = methods
    }
    private suspend fun requestList() {
        val response = sendRequest(Request(Method.LIST, "*", Protocol.Current, nextCSeq()))

        list = mapOf()

        if(response.contentType != "text/parameters") {
            throw Error("Invalid \"content-type\" = \"${response.contentType}\" in LIST response")
        }

        list = Parser.ParseParameters(response.body) ?: run {
            throw Error("Fail parse LIST response")
        }
    }
    private suspend fun requestDescribe(): String = requestDescribe("*")
    private suspend fun requestDescribe(uri: String): String {
        this.uri = uri

        val response = sendRequest(Request(Method.DESCRIBE, uri, Protocol.Current, nextCSeq()))

        sessionId = response.sessionId ?: run {
            throw Error("Missing Session in DESCRIBE response")
        }
        if(response.contentType != "application/sdp") {
            throw Error("Invalid \"content-type\" = \"${response.contentType}\" in DESCRIBE response")
        }
        if(response.body.isEmpty()) {
            throw Error("Missing SDP in DESCRIBE response")
        }

        return response.body
    }

    private suspend fun requestPlay(sdp: String) {
        val uri = this.uri ?: throw IllegalStateException("Unknown URI on PLAY request")
        val sessionId = this.sessionId ?: throw IllegalStateException("Unknown Session Id on PLAY request")

        val headerFields = mapOf(ContentTypeField("application/sdp"))
        val request = Request(Method.PLAY, uri, Protocol.Current, nextCSeq(), sessionId, headerFields, sdp)
        sendRequest(request)
    }

    private suspend fun requestSetup(mlineIndex: Int, candidate: String) {
        val uri = this.uri ?: throw IllegalStateException("Unknown URI on PLAY request")
        val sessionId = this.sessionId ?: throw IllegalStateException("Unknown Session Id on PLAY request")

        val headerFields = mapOf(ContentTypeField("application/x-ice-candidate"))
        val body = "$mlineIndex/$candidate\r\n"
        val request = Request(Method.SETUP, uri, Protocol.Current, nextCSeq(), sessionId, headerFields, body)
        sendRequest(request)
    }

    fun onConnected() {
        scope.launch {
            requestOptions("*")
            if(supportedMethods.contains(Method.LIST))
                requestList()

            val sdp = requestDescribe()

            val localPeer = createPeer()
            this@Session.localPeer = localPeer

            localPeer.onIceCandidate { mlineIndex, candidate ->
                scope.launch {
                    requestSetup(mlineIndex, candidate)
                }
            }

            localPeer.setRemoteSdp(sdp)
            val answerSdp = localPeer.getLocalSdp()
            requestPlay(answerSdp)
        }
    }
}
