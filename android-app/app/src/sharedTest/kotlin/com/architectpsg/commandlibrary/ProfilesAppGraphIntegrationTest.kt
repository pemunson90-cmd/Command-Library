package com.architectpsg.commandlibrary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.di.AppGraph
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual application graph and Room CRUD. Host JNI proof is separate from device execution. */
@RunWith(AndroidJUnit4::class)
class ProfilesAppGraphIntegrationTest {
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun <T> ok(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }

    @Test fun actualApplicationCachesCompleteProfilePortAndPersistsRevisionCheckedCrud() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CommandLibraryApplication
        val ports = coroutineScope {
            val a = async { ok(app.graph.ports()) }
            val b = async { ok(app.graph.ports()) }
            a.await().also { assertSame(it, b.await()) }
        }
        val repo = requireNotNull(ports.playerProfiles) { "Actual AppGraph must expose the complete profile repository" }
        assertSame(repo, ok(app.graph.ports()).playerProfiles)
        assertTrue(app.getDatabasePath(AppGraph.DATABASE_FILE).isFile)
        val life = CounterDefinition(EntityId("life"), "Life", null, null, -10, CounterLossPolicy.AtOrBelow(0), false, CounterRole.LIFE)
        val draft = PlayerProfileDraft("  Éve ${UUID.randomUUID()}  ", -10, SnapshotList.of(life), SeatAppearance(null,null), SnapshotList.empty(), 3)
        var owned: EntityId? = null
        try {
            val created = ok(repo.create(draft, token)); owned = created.id
            assertEquals(draft, ok(repo.read(created.id, token)).value)
            var cursor: String? = null
            var found = false
            val seenCursors = mutableSetOf<String?>()
            do {
                assertTrue("Real paging must advance", seenCursors.add(cursor))
                assertTrue("Integration probe paging budget exceeded", seenCursors.size <= 10_000)
                val page = ok(repo.list(PageRequest(100,cursor), token))
                found = found || page.items.any { it == created }
                cursor = page.nextCursor
            } while (!found && cursor != null)
            assertTrue("Created row must appear through real paging", found)
            val updated = ok(repo.update(created.id, draft.copy(name=draft.name + " edited"), created.revision, token))
            assertEquals(Revision(1), updated.revision)
            assertEquals(updated, ok(repo.read(created.id, token)))
            val stale = repo.update(created.id, draft, created.revision, token)
            assertTrue(stale is Outcome.Failure)
            assertEquals(FailureCode.STALE_REVISION, (stale as Outcome.Failure).error.code)
            assertEquals(updated, ok(repo.read(created.id, token)))
            ok(repo.delete(updated.id, updated.revision, token)); owned = null
            val missing = repo.read(updated.id, token)
            assertTrue(missing is Outcome.Unavailable)
            assertEquals(UnavailableReason.NOT_FOUND, (missing as Outcome.Unavailable).reason)
        } finally {
            owned?.let { id ->
                val remaining = repo.read(id, token)
                if (remaining is Outcome.Success) ok(repo.delete(id, remaining.value.revision, token))
            }
        }
    }
}
