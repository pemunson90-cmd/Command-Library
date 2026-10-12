package com.architectpsg.commandlibrary.feature.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import kotlin.time.Duration

@RunWith(AndroidJUnit4::class)
class LocalCatalogInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val bytes get() = context.assets.open("english-three-printings.json").use { it.readBytes() }
    private class TestClock : Clock {
        private var now = 1000L
        override fun utcNow(): UtcTimestamp = UtcTimestamp(now++)
        override fun monotonicNow(): Duration = Duration.ZERO
    }
    private class TestIds : IdSource { override fun nextId(): String = UUID.randomUUID().toString() }
    private class TestCancel : CancelToken {
        var cancelled = false
        override val isCancelled: Boolean get() = cancelled
        override fun throwIfCancelled() { if (cancelled) throw CancellationException("cancelled in test") }
    }
    private fun source(bytes: ByteArray, checksum: String? = null) = CatalogImportSource(
        ResourceUri("content://catalog-test/cards"), "verified-english-three-printings.json", ProviderId("scryfall"), LanguageTag.ENGLISH,
        Knowledge.Unknown("Dataset date not supplied", SnapshotList.empty()), Knowledge.Absent("Upstream URI not supplied"),
        if (checksum == null) Knowledge.Absent("No expected checksum") else Knowledge.Known(ContentHash("SHA-256", checksum)),
        Knowledge.Known(bytes.size.toLong()), Knowledge.Absent("Notice not supplied"),
    )
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun chunks(bytes: ByteArray) = flowOf(ByteChunk(0, 0, SnapshotBytes(bytes), null, true))
    private suspend fun open(name: String): StorageDatabase = (StorageDatabase.open(context, name) as Outcome.Success).value

    @Test fun genuineImportLookupAndReopen() = runBlocking {
        val name = "catalog-${UUID.randomUUID()}.db"
        val data = bytes
        val token = TestCancel()
        val db = open(name)
        try {
            val catalog = LocalCatalog(db, TestClock(), TestIds(), CatalogImportLimits(batchSize = 1))
            assertFalse(((catalog.status(token) as Outcome.Success).value).initialized)
            val imported = catalog.`import`(source(data, hash(data)), chunks(data), {}, token) as Outcome.Success
            assertEquals(2, imported.value.counts.oracleCards)
            assertEquals(3, imported.value.counts.printings)
            assertEquals(2, imported.value.counts.faces)
            assertEquals(ChecksumVerification.MATCHED_EXPECTED, imported.value.verification.checksumVerification)
            val page = catalog.lookupName("Lightning", PageRequest(1), token) as Outcome.Success
            assertEquals(1, page.value.items.size)
            assertEquals("Lightning Bolt", page.value.items[0].name)
            assertTrue(page.value.items[0].provenance.sourceDate is Knowledge.Unknown)
        } finally { db.close() }
        val reopened = open(name)
        try {
            val catalog = LocalCatalog(reopened, TestClock(), TestIds())
            val status = catalog.status(token) as Outcome.Success
            assertTrue(status.value.initialized)
            assertEquals(3, status.value.activeGenerations.single().counts.printings)
            assertEquals(1, (catalog.lookupName("Delver", PageRequest(10), token) as Outcome.Success).value.items.size)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun checksumFailureAndCancellationAfterWriteKeepPriorGeneration() = runBlocking {
        val name = "catalog-${UUID.randomUUID()}.db"
        val data = bytes
        val db = open(name)
        try {
            val token = TestCancel()
            val catalog = LocalCatalog(db, TestClock(), TestIds(), CatalogImportLimits(batchSize = 1))
            val first = catalog.`import`(source(data), chunks(data), {}, token) as Outcome.Success
            val failed = catalog.`import`(source(data, "0".repeat(64)), chunks(data), {}, token)
            assertTrue(failed is Outcome.Failure)
            assertEquals(first.value.generation.id, (catalog.status(token) as Outcome.Success).value.activeGenerations.single().id)
            val cancel = TestCancel()
            try {
                catalog.`import`(source(data), chunks(data), { if (it.completedUnits >= 1) cancel.cancelled = true }, cancel)
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertEquals(first.value.generation.id, (catalog.status(token) as Outcome.Success).value.activeGenerations.single().id)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun syntheticPagingCursorAndLiteralPunctuation() = runBlocking {
        val name = "catalog-${UUID.randomUUID()}.db"
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        val synthetic = JSONArray()
        synthetic.put(array.getJSONObject(0))
        val second = org.json.JSONObject(array.getJSONObject(0).toString())
        second.put("id", "33333333-3333-4333-8333-333333333333")
        second.put("oracle_id", "44444444-4444-4444-8444-444444444444")
        second.put("name", "Lightning Echo")
        second.put("oracle_text", "Synthetic test only")
        synthetic.put(second)
        val data = synthetic.toString().toByteArray(Charsets.UTF_8)
        val db = open(name)
        try {
            val token = TestCancel()
            val catalog = LocalCatalog(db, TestClock(), TestIds(), CatalogImportLimits(batchSize = 1))
            assertTrue(catalog.`import`(source(data), chunks(data), {}, token) is Outcome.Success)
            val first = (catalog.lookupName("Lightning", PageRequest(1), token) as Outcome.Success).value
            assertEquals("Lightning Bolt", first.items.single().name)
            assertNotNull(first.nextCursor)
            val secondPage = (catalog.lookupName("Lightning", PageRequest(1, first.nextCursor), token) as Outcome.Success).value
            assertEquals("Lightning Echo", secondPage.items.single().name)
            assertNull(secondPage.nextCursor)
            assertTrue(catalog.lookupName("Delver", PageRequest(1, first.nextCursor), token) is Outcome.Failure)
            assertTrue(catalog.lookupName("Lightning", PageRequest(1, "ZZ"), token) is Outcome.Failure)
            assertEquals(1, (catalog.lookupName("\"Lightning!\"", PageRequest(1), token) as Outcome.Success).value.items.size)
            assertTrue(catalog.`import`(source(bytes), chunks(bytes), {}, token) is Outcome.Success)
            val stale = catalog.lookupName("Lightning", PageRequest(1, first.nextCursor), token) as Outcome.Failure
            assertEquals(FailureCode.STALE_GENERATION, stale.error.code)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
