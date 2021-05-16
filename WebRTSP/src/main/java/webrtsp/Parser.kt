package webrtsp

import org.webrtc.IceCandidate
import java.lang.Error
import kotlin.math.min
import kotlin.math.max

private typealias HeaderField = Pair<String, String>

private data class MethodLine(
    val method: Method,
    val uri: String,
    val protocol: Protocol,
)

private data class StatusLine(
    val protocol: Protocol,
    val statusCode: StatusCode,
    val reasonPhrase: String,
)

class Parser(val buffer: String) {
    companion object {
        private fun IsWSP(char: Char) = char == ' ' || char == '\t'
        private fun ParseMethod(token: Token): Method? {
            if(token.empty) return null

            for(method in Method.values()) {
                if(token.equals(method.name)) {
                    return method
                }
            }

            return null
        }
        private fun ParseProtocol(token: Token): Protocol? {
            if(token.empty) return null

            for(protocol in Protocol.values()) {
                if(token.equals(protocol.rawName)) {
                    return protocol
                }
            }

            return null
        }
        private fun ParseCSeq(strCSeq: String): CSeq? {
            var cseq = 0

            for(c in strCSeq) {
                if(!c.isDigit()) return null

                val digit = c.digitToInt()

                if(cseq > (cseq * 10 + digit)) {
                    // overflow
                    return null
                }

                cseq = cseq * 10 + digit
            }

            if(cseq == 0) return null

            return cseq
        }
        private fun ParseStatusCode(token: String): StatusCode? {
            if(token.length < 3) return null

            return token[0].digitToInt() * 100 + token[1].digitToInt() * 10 + token[2].digitToInt() * 1
        }
        fun ParseParameters(body: String): Map<String, String>? {
            val parameters = mutableMapOf<String, String>();

            val parser = Parser(body);
            while(!parser.eos) {
                val pair = parser.getParameter() ?: return null
                if(pair.first.isEmpty()) return null

                parameters.set(pair.first, pair.second);
            }

            return parameters;
        }
        fun ParseOptions(options: String): Set<Method>? {
            val parsedOptions = mutableSetOf<Method>()

            val parser = Parser(options)
            while(!parser.eos) {
                parser.skipWSP()

                val methodToken = parser.getToken() ?: return null
                val method = ParseMethod(methodToken) ?: return null
                parser.skipWSP()

                if(!parser.eos && !parser.skip(','))
                    return null

                parsedOptions.add(method)
            }

            return parsedOptions;
        }
        fun ParseIceCandidates(iceCandidatesMessage: String): List<Pair<Int, String>>? {
            val iceCandidatesList = iceCandidatesMessage.lines()

            val iceCandidates = try {
                iceCandidatesList.mapNotNull { candidateRow ->
                    if(candidateRow.isEmpty()) {
                        null
                    } else {
                        val fields = candidateRow.split("/", limit = 2)
                        if(fields.size != 2) throw Error("Invalid Ice Candidate line format")
                        val mLineIndex = fields[0].toInt()
                        Pair(mLineIndex, fields[1])
                    }
                }
            } catch (e: Throwable) {
                null
            }

            return iceCandidates
        }
        fun IsRequest(message: String): Boolean {
            val parser = Parser(message)
            val methodToken = parser.getToken() ?: return false
            ParseMethod(methodToken) ?: return false
            return true
        }
        fun ParseRequest(message: String): Request? {
            val parser = Parser(message)

            val methodLine = parser.getMethodLine() ?: return null

            val headerFields = mutableMapOf<String, String>()
            while(!parser.eos) {
                val headerField = parser.getHeaderField() ?: return null
                headerFields[headerField.first] = headerField.second
                if(parser.eos) break // no body
                if(parser.skipEOL()) break // empty line before body
            }

            val body = if(!parser.eos) parser.tail else String()

            val strCSeq = headerFields["cseq"] ?: return null
            val cSeq = ParseCSeq(strCSeq) ?: return null
            headerFields.remove("cseq")

            val sessionId: String? = headerFields["session"]
            if(sessionId != null) headerFields.remove("session")

            return Request(
                methodLine.method, methodLine.uri, methodLine.protocol,
                cSeq, sessionId, headerFields,
                body)
        }
        fun ParseResponse(message: String): Response? {
            val parser = Parser(message)

            val statusLine = parser.getStatusLine() ?: return null

            val headerFields = mutableMapOf<String, String>()
            while(!parser.eos) {
                val headerField = parser.getHeaderField() ?: return null
                headerFields[headerField.first] = headerField.second
                if(parser.eos) break // no body
                if(parser.skipEOL()) break // empty line before body
            }

            val body = if(!parser.eos) parser.tail else String()

            val strCSeq = headerFields["cseq"] ?: return null
            val cSeq = ParseCSeq(strCSeq) ?: return null
            headerFields.remove("cseq")

            val sessionId: String? = headerFields["session"]
            if(sessionId != null) headerFields.remove("session")

            return Response(
                statusLine.protocol, statusLine.statusCode, statusLine.reasonPhrase,
                cSeq, sessionId, headerFields,
                body)
        }
    }

    private var pos: Int = 0
        private set(value) {
            field = min(value, buffer.length)
        }

    private val eos get() = pos >= buffer.length
    private val length get() = buffer.length
    private val tailLength get() = buffer.length - pos
    private val tail get() = buffer.substring(pos)

    private val currentChar get() = buffer[pos]
    private val currentDigit get() = currentChar.digitToInt()

    private val isCurrentSpace get()  = currentChar == ' '
    private val isCurrentWSP get()  = IsWSP(currentChar)
    private val isCurrentCtl get() = CharRange(0.toChar(), 31.toChar()).contains(currentChar) || currentChar == 127.toChar()
    private val isCurrentDigit get() = currentChar.isDigit()
    private val isCurrentTspecials: Boolean get() {
        return when(currentChar) {
            '(',
            ')',
            '<',
            '>',
            '@',
            ',',
            ';',
            ':',
            '\\',
            '"',
            '/',
            '[',
            ']',
            '?',
            '=',
            '{',
            '}',
            ' ',
            '\t' -> true
            else -> false
        }
    }

    private fun clone(): Parser {
        val parserClone = Parser(buffer)
        parserClone.pos = pos
        return parserClone
    }

    private fun get(index: Int): Char = buffer.get(index.toInt())

    private fun startsWith(prefix: String): Boolean {
        return buffer.startsWith(prefix, pos)
    }
    private fun substringFrom(beginPos: Int): String {
        return buffer.substring(beginPos, pos)
    }
    private fun substring(beginPos: Int, endPos: Int): String {
        return buffer.substring(beginPos, endPos)
    }

    private fun advance(count: Int = 1) {
        pos += max(count, 0)
    }
    private fun skipWSP(): Boolean {
        val savePos = pos

        while(pos < length && IsWSP(currentChar)) {
            advance()
        }

        return savePos != pos
    }
    private fun skipEOL(): Boolean {
        return when(currentChar) {
            '\n' -> {
                advance()

                true
            }
            '\r' -> {
                advance()
                if(!eos && currentChar == '\n')
                    advance()

                true
            }
            else -> false
        }
    }
    private fun skipFolding(): Boolean {
        val tmpParseBuffer = clone()

        if(!tmpParseBuffer.skipEOL())
            return false
        if(!tmpParseBuffer.skipWSP())
            return false

        pos = tmpParseBuffer.pos
        return true
    }
    private fun skipLWS(): Boolean {
        val tmpParseBuffer = clone()

        tmpParseBuffer.skipEOL()
        if(!tmpParseBuffer.skipWSP())
            return false

        pos = tmpParseBuffer.pos
        return true
    }
    private fun skip(c: Char): Boolean {
        if(eos)
            return false

        if(currentChar == c) {
            advance()
            return true
        }

        return false
    }
    private fun skipNot(c: Char): Boolean {
        while(!eos) {
            if(currentChar == c)
                return true

            advance()
        }

        return false
    }

    private fun getToken(): Token? {
        var token = Token(buffer, pos)

        while(!eos) {
            if(isCurrentCtl || isCurrentTspecials) break
            advance()
        }

        token.length = pos - token.pos

        return if(!token.empty)
            token
        else
            null
    }
    private fun getProtocolToken(): Token?
    {
        var token  = Token(buffer, pos)

        val protocolName = "WEBRTSP"

        if(tailLength < Protocol.Current.rawName.length) return null

        if(!startsWith(protocolName)) return null
        advance(protocolName.length)

        if(currentChar != '/') return null
        advance()

        if(!isCurrentDigit) return null
        advance()

        if(currentChar != '.') return null
        advance()

        if(!isCurrentDigit) return null
        advance()

        token.length = pos - token.pos

        return token
    }
    private fun getProtocol(): Protocol? {
        val token = getProtocolToken() ?: return null
        return ParseProtocol(token)
    }
    private fun getURIToken(): Token? {
        // FIXME! fix according to rfc

        val uriToken = Token(buffer, pos)

        while(!eos) {
            if(isCurrentCtl || isCurrentSpace)
                break
           advance()
        }

        uriToken.length = pos - uriToken.pos

        return if(!uriToken.empty)
            uriToken
        else
            null
    }
    private fun getURI(): String? {
        val token = getURIToken() ?: return null
        return token.value
    }
    private fun getStatusCodeToken(): Token? {
        if(tailLength < 3) return null

        val token = Token(buffer, pos)

        var i = 0
        while(i < 3 && !eos) {
            if(!isCurrentDigit)
                return null

            ++i
            advance()
        }

        token.length = 3

        return token
    }
    private fun getStatusCode(): StatusCode? {
        val token = getStatusCodeToken() ?: return null
        return ParseStatusCode(token.value)
    }
    private fun getReasonPhraseToken(): Token? {
        val token = Token(buffer, pos)

        while(!eos) {
            if(isCurrentCtl) break
            advance()
        }

        token.length = pos - token.pos

        return token
    }
    private fun getReasonPhrase(): String? {
        val token = getReasonPhraseToken() ?: return null
        return token.value
    }
    private fun getMethodLine(): MethodLine? {
        val methodToken = getToken() ?: return null
        val method = ParseMethod(methodToken) ?: return null
        if(!skipWSP()) return null
        val uri = getURI() ?: return null
        if(!skipWSP()) return null
        val protocol = getProtocol() ?: return null
        if(!skipEOL()) return null

        return MethodLine(method, uri, protocol)
    }
    private fun getHeaderField(): HeaderField? {
        val nameToken = getToken() ?: return null
        if(!skip(':')) return null
        skipLWS()
        val valueToken = Token(buffer, pos)
        while(!eos) {
            val tmpPos = pos
            if(skipFolding())
                continue
            else if(skipEOL()) {
                val lowerName = nameToken.value.lowercase()
                valueToken.length = tmpPos - valueToken.pos

                return HeaderField(lowerName, valueToken.value)
            } else if(!isCurrentCtl)
                advance()
            else
                return null
        }

        return null
    }
    private fun getParameter(): Parameter? {
        val namePos = pos

        if(!skipNot(':')) return null

        val name = substringFrom(namePos)
        if(name.isBlank()) return null

        if(!skip(':')) return null

        skipWSP()

        val valuePos = pos

        while(!eos) {
            val tmpPos = pos
            if(skipEOL()) {
                val value = substring(valuePos, tmpPos);
                return Parameter(name, value)
            } else if(!isCurrentCtl)
                advance()
            else
                return null
        }

        return null
    }
    private fun getStatusLine(): StatusLine? {
        val protocol = getProtocol() ?: return null
        if(!skipWSP()) return null
        val statusCode = getStatusCode() ?: return null
        if(!skipWSP()) return null
        val reasonPhrase = getReasonPhrase() ?: return null
        if(!skipEOL()) return null
        return StatusLine(protocol, statusCode, reasonPhrase)
    }
}
