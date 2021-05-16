package webrtsp

class Serializer {
    companion object {
        private fun SerializeStatusCode(statusCode: StatusCode): String {
            return if(statusCode > 999) "999"
            else if(statusCode < 100) "100"
            else statusCode.toString()
        }

        fun Serialize(request: Request): String {
            val out = StringBuilder()
            out.append("${request.method} ${request.uri} ${request.protocol.rawName}\r\n")
            out.append("CSeq: ${request.cSeq}\r\n")

            if(request.sessionId != null)
                out.append("Session: ${request.sessionId}\r\n");

            request.headerFields.forEach { (key, value) ->
                out.append("$key: $value\r\n")
            }

            if(request.body.isNotEmpty()) {
                out.append("\r\n")
                out.append(request.body)
            }

            return out.toString()
        }

        fun Serialize(response: Response): String {
            val out = StringBuilder()

            out.append("${response.protocol.rawName} ${SerializeStatusCode(response.statusCode)} ${response.reasonPhrase}\r\n")
            out.append("CSeq: ${response.cSeq}\r\n")

            if(response.sessionId != null)
                out.append("Session: ${response.sessionId}\r\n");

            response.headerFields.forEach { (key, value) ->
                out.append("$key: $value\r\n")
            }

            if(response.body.isNotEmpty()) {
                out.append("\r\n")
                out.append(response.body)
            }

            return out.toString()
        }
    }
}

