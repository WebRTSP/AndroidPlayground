package webrtsp

typealias IceCandidateCallback = (mlineIndex: Int, candidate: String) -> Unit
typealias EosCallback = () -> Unit

interface WebRTCPeer {
    fun onIceCandidate(onIceCandidate: IceCandidateCallback)
    fun onEos(onEos: EosCallback)

    suspend fun setRemoteSdp(sdp: String)
    suspend fun getLocalSdp(): String
    suspend fun addIceCandidate(mlineIndex: Int, candidate: String)
    fun close()
}

