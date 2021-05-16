package webrtsp

typealias CSeq = Int
typealias SessionId = String
typealias Parameter = Pair<String, String>
typealias HeaderFields = Map<String, String>
typealias ContentType = String

interface MessageCommon
{
    val cSeq: CSeq
    val sessionId: SessionId?
    val headerFields: HeaderFields
    val body: String

    val contentType: ContentType?
        get() = headerFields["content-type"]
}

data class Request (
    val method: Method,
    val uri: String,
    val protocol: Protocol,
    override val cSeq: CSeq,
    override val sessionId: SessionId? = null,
    override val headerFields: HeaderFields = mapOf(),
    override val body: String = String()
) : MessageCommon

data class Response(
    val protocol: Protocol,
    val statusCode: StatusCode,
    val reasonPhrase: String,
    override val cSeq: CSeq,
    override val sessionId: SessionId?,
    override val headerFields: HeaderFields = mapOf(),
    override val body: String = String()
) : MessageCommon

fun ContentTypeField(contentType: ContentType) = Pair("content-type", contentType)
