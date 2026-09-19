package com.maepranam.networkprinter.job

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintJobParserTest {
    private val parser = PrintJobParser(Gson())

    @Test
    fun acceptsHttpsFullPath() {
        val receipt = parser.parse("{\"fullPath\":\"https://shop.test/r.png\"}").getOrThrow()

        assertEquals("https://shop.test/r.png", receipt.source.toString())
    }

    @Test
    fun acceptsHttpAndTrimsWhitespace() {
        val receipt = parser.parse("{\"fullPath\":\"  http://192.168.1.2/r.png  \"}").getOrThrow()

        assertEquals("http://192.168.1.2/r.png", receipt.source.toString())
    }

    @Test
    fun rejectsMalformedMissingBlankHostlessAndNonWebPayloads() {
        listOf(
            "",
            "not json",
            "{}",
            "{\"fullPath\":\" \"}",
            "{\"fullPath\":\"/relative/r.png\"}",
            "{\"fullPath\":\"file:///tmp/a.png\"}",
        ).forEach { payload ->
            assertTrue("Expected failure for $payload", parser.parse(payload).isFailure)
        }
    }
}
