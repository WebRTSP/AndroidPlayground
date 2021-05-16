package webrtsp

enum class Protocol(val rawName: String) {
    WEBRTSP_0_2("WEBRTSP/0.2"),
    Current(WEBRTSP_0_2.rawName)
};
