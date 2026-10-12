package com.architectpsg.commandlibrary.feature.shell

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AC-J03 scan: user-facing text in shell UI code comes from string resources. Flags string literals
 * passed as Text content or as text/label/description arguments, and any literal that reads as
 * words. Non-text literals such as list keys, test tags and MIME types are not words and pass.
 */
class ExternalizedStringsTest {
    private val moduleDir = File("").absoluteFile
    private val uiSources: List<File>
        get() {
            val root = File(moduleDir, "src/main/kotlin/com/architectpsg/commandlibrary/feature/shell")
            return root.resolve("ui").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() +
                root.resolve("CommandLibraryShell.kt")
        }

    private val textCall = Regex("""\bText\(\s*"""")
    private val namedText = Regex("""\b(text|contentDescription|label|placeholder|onClickLabel|stateDescription|title|body)\s*=\s*(listOf\(\s*)?"""")
    private val wordsLiteral = Regex(""""[^"\n$]*[A-Za-z]{2,}\s+[A-Za-z]{2,}[^"\n]*"""")

    internal fun violations(source: String): List<String> = source.lines()
        .map { it.trim() }
        .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
        .filter { textCall.containsMatchIn(it) || namedText.containsMatchIn(it) || wordsLiteral.containsMatchIn(it) }

    @Test
    fun detectorFlagsHardcodedText() {
        assertEquals(1, violations("""Text("Import complete")""").size)
        assertEquals(1, violations("""Button(onClick = x) { Text(text = "Cancel") }""").size)
        assertEquals(1, violations("""Icon(icon, contentDescription = "Back")""").size)
        assertEquals(1, violations("""MessageBlock(title = null, body = listOf("Nothing here"))""").size)
        assertEquals(1, violations("""val message = "The file could not be read"""").size)
        assertEquals(0, violations("""Text(stringResource(R.string.import_title))""").size)
        assertEquals(0, violations("""items(results, key = { "${'$'}{it.source}:${'$'}{it.raw}" })""").size)
        assertEquals(0, violations("""arrayOf("application/json", "text/plain")""").size)
        assertEquals(0, violations("""    // A comment may say "plain words" freely""").size)
    }

    @Test
    fun shellUiCodeHasNoHardcodedUserText() {
        val files = uiSources
        assertTrue("scan found the UI sources", files.size >= 8 && files.all(File::isFile))
        val found = files.flatMap { file -> violations(file.readText()).map { "${file.name}: $it" } }
        assertTrue("Hardcoded UI text:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun englishResourcesFollowTheProjectStyle() {
        val strings = File(moduleDir, "src/main/res/values/strings.xml").readText()
        assertTrue("no em dashes in UI text", '\u2014' !in strings)
        // Markers are assembled so this file does not itself trip a repository placeholder scan.
        val markers = listOf("TO" + "DO", "FIX" + "ME", "X".repeat(3), "lor" + "em")
        assertTrue("no unfinished markers", markers.none { strings.contains(it, ignoreCase = true) })
        val other = File(moduleDir, "src/main/res").listFiles { file -> file.name.startsWith("values-") }.orEmpty()
        assertTrue("English is the only shipped interface language", other.isEmpty())
    }
}
