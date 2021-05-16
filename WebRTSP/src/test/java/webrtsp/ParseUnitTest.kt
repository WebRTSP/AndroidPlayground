package webrtsp

import org.junit.Test
import org.junit.Assert.*
import webrtsp.parser.Method
import webrtsp.parser.Parser
import webrtsp.parser.Protocol

class ParseUnitTest {
    @Test
    fun optionsRequestParseTest() {
        val message =
            "OPTIONS * WEBRTSP/0.2\r\n" +
            "CSeq: 1\r\n"
        val request = Parser.ParseRequest(message)

        assertNotNull(request)
        if(request == null) return

        assertTrue(Method.OPTIONS == request.method)
        assertEquals("*", request.uri)
        assertEquals(Protocol.WEBRTSP_0_2, request.protocol)
        assertEquals(1, request.cSeq)
        assertTrue(request.headerFields.isEmpty())
    }
    @Test
    fun optionsRequestParse2Test() {
        val message =
            "OPTIONS * WEBRTSP/0.2\r\n" +
                    "CSeq:\t1\r\n"
        val request = Parser.ParseRequest(message)

        assertNotNull(request)
        if(request == null) return

        assertTrue(Method.OPTIONS == request.method)
        assertEquals("*", request.uri)
        assertEquals(Protocol.WEBRTSP_0_2, request.protocol)
        assertEquals(1, request.cSeq)
        assertTrue(request.headerFields.isEmpty())
    }
    @Test
    fun getParameterRequestParseTest() {
        val message =
            "GET_PARAMETER rtsp://example.com/media.mp4 WEBRTSP/0.2\r\n" +
            "CSeq: 9\r\n" +
            "Content-Type: text/parameters\r\n" +
            "Session: 12345678\r\n" +
            "Content-Length: 15\r\n" +
            "\r\n" +
            "packets_received\r\n" +
            "jitter\r\n"

        val request = Parser.ParseRequest(message)
        assertNotNull(request)
        if(request == null) return

        assertTrue(Method.GET_PARAMETER == request.method)
        assertEquals(9, request.cSeq)
        assertEquals("12345678", request.sessionId)
        assertEquals(2, request.headerFields.size)
        assertTrue(request.body.isNotEmpty())
    }
}
