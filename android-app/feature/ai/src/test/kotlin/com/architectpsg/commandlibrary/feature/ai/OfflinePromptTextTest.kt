package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class OfflinePromptTextTest {
    @Test fun templatesUseExactMachineMappingAndNonrecursiveBindings() {
        val templates = OfflinePromptText.templates()
        assertEquals(OfflineLabMode.entries.map { it.templateId }, templates.map { it.id })
        for (template in templates) {
            assertEquals(1, template.shippedVersion)
            val rendered = OfflinePromptText.bind(template.body, mapOf("deck" to "{{request}}", "constraints" to "C", "request" to "R"), noCancel)
            assertTrue(rendered.contains("{{request}}"))
            assertTrue(rendered.contains("[[exact card name]]"))
        }
    }
    @Test fun malformedUnknownDuplicateAndMissingVariablesFail() {
        for (body in listOf("{{deck}} {{request}}", "{{deck}} {{constraints}} {{request}} {{deck}}", "{{deck}} {{constraints}} {{request}} {{other}}", "{{deck}} {{constraints}} {{request}", "{{deck}} {{constraints}} {{request}} }}")) {
            try { OfflinePromptText.bind(body, mapOf("deck" to "D", "constraints" to "C", "request" to "R"), noCancel); fail(body) }
            catch (_: OfflineInputException) { }
        }
    }
    @Test fun strictUnicodeAndJsonEscaping() {
        assertEquals("\"a\\n\\\"\\\\\\u0000😀\"", OfflinePromptText.json("a\n\"\\\u0000😀", noCancel))
        for (input in listOf("\uD800", "\uDC00", "x".repeat(32769))) {
            try { OfflinePromptText.utf8Size(input, 32768, noCancel); fail() } catch (_: OfflineInputException) { }
        }
    }
}

internal val noCancel = object : CancelToken {
    override val isCancelled = false
    override fun throwIfCancelled() = Unit
}
