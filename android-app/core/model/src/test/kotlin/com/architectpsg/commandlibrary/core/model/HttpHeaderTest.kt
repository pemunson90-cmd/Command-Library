package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpHeaderTest {
    @Test fun refusesHeaderInjectionAndInvalidTokenNames() {
        listOf("", " ", "Bad Name", "Header:Other", "Header\rInjected", "Header\nInjected", "Héader", "Header\tName").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { HttpHeader(name, "value") }
        }
        listOf("value\rInjected: secret", "value\nInjected: secret", "value\r\nInjected: secret").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { HttpHeader("Valid-Name", value) }
        }
    }
    @Test fun preservesLegitimateHeaderNamesAndValues() {
        assertEquals("Bearer user-key", HttpHeader("Authorization", "Bearer user-key").value)
        assertEquals("", HttpHeader("X-Empty", "").value)
        assertEquals("!#$%&'*+-.^_`|~", HttpHeader("!#$%&'*+-.^_`|~", "token").name)
    }
}
