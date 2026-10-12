package com.architectpsg.commandlibrary.feature.settings

import com.architectpsg.commandlibrary.core.common.CoroutineCancelToken
import com.architectpsg.commandlibrary.core.model.CachePolicy
import com.architectpsg.commandlibrary.core.model.Clock
import com.architectpsg.commandlibrary.core.model.ContentHash
import com.architectpsg.commandlibrary.core.model.DecimalAmount
import com.architectpsg.commandlibrary.core.model.DecimalUnit
import com.architectpsg.commandlibrary.core.model.DownloadKind
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.SettingChange
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.StorageMode
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val fixtures = mutableListOf<Fixture>()

    private fun fixture(): Fixture = Fixture(temporary.newFolder()).also { fixtures += it }

    @After
    fun releaseStores() {
        fixtures.forEach { it.closeAll() }
    }

    private suspend fun assertCancelled(block: suspend () -> Unit) {
        try {
            block()
        } catch (expected: CancellationException) {
            return
        }
        fail("expected a CancellationException")
    }

    private fun writeStore(fixture: Fixture, bytes: ByteArray) {
        fixture.storeFile.parentFile!!.mkdirs()
        fixture.storeFile.writeBytes(bytes)
    }

    private fun filesUnder(directory: File): Set<String> =
        directory.walkTopDown().filter { it.isFile }.map { it.name }.toSet()

    // ---- defaults ----

    @Test
    fun firstReadReturnsTheDocumentedDefaultsAndWritesNoSettingsFile(): Unit = blocking {
        val f = fixture()
        val snapshot = f.repo.read(LiveToken).success()
        assertEquals(Revision(0), snapshot.revision)
        assertEquals(LanguageTag.ENGLISH, snapshot.dataLanguage)
        assertEquals(ThemePreference.SYSTEM, snapshot.theme)
        assertEquals(StorageMode.FULL, snapshot.storageMode)
        assertEquals(CachePolicy(imageBytes = 0, retainRulings = false, wifiOnlyDownloads = true), snapshot.cache)
        assertTrue(snapshot.budget is Knowledge.Unknown)
        assertTrue(snapshot.providerProfileIds.isEmpty())
        assertTrue(snapshot.sourceNotices.isEmpty())
        assertTrue(snapshot.consents.isEmpty())
        assertEquals(UtcTimestamp(0), snapshot.updatedAt)
        assertFalse("a read must not create the settings file", f.storeFile.exists())
        UUID.fromString(f.idFile.readText())
    }

    // ---- update ----

    @Test
    fun updateWithMatchingRevisionCommitsEveryChangeAndIncrementsOnce(): Unit = blocking {
        val f = fixture()
        val committedAt = UtcTimestamp(1_760_000_100, 5)
        f.clock.now = committedAt
        val budget = Knowledge.Known(DecimalAmount.exact("25.00", DecimalUnit.Fiat("USD")))
        val profiles = SnapshotList.of(EntityId("profile-a"), EntityId("profile-b"))
        val first = f.repo.update(
            patch(
                SettingChange.Theme(ThemePreference.DARK),
                SettingChange.DataLanguage(LanguageTag("de")),
                SettingChange.Storage(StorageMode.LEAN),
                SettingChange.Cache(CachePolicy(imageBytes = 1_000, retainRulings = true, wifiOnlyDownloads = false)),
                SettingChange.Budget(budget),
                SettingChange.ProviderProfiles(profiles),
            ),
            Revision(0),
            LiveToken,
        ).success()
        assertEquals(Revision(1), first.revision)
        assertEquals(ThemePreference.DARK, first.theme)
        assertEquals(LanguageTag("de"), first.dataLanguage)
        assertEquals(StorageMode.LEAN, first.storageMode)
        assertEquals(CachePolicy(1_000, true, false), first.cache)
        assertEquals(budget, first.budget)
        assertEquals(profiles, first.providerProfileIds)
        assertEquals(committedAt, first.updatedAt)

        val second = f.repo.update(patch(SettingChange.Theme(ThemePreference.LIGHT)), Revision(1), LiveToken).success()
        assertEquals(Revision(2), second.revision)
        assertEquals(ThemePreference.LIGHT, second.theme)
        assertEquals(StorageMode.LEAN, second.storageMode)

        assertEquals("a new instance on the same files reads what was committed", second, f.reopen().read(LiveToken).success())
        assertEquals(Revision(2), SettingsStoreCodec.decode(f.storeFile.readBytes()).revision)
    }

    @Test
    fun changesInOnePatchApplyInOrderAndCountAsOneRevision(): Unit = blocking {
        val f = fixture()
        val result = f.repo.update(
            patch(SettingChange.Theme(ThemePreference.DARK), SettingChange.Theme(ThemePreference.LIGHT)),
            Revision(0),
            LiveToken,
        ).success()
        assertEquals(ThemePreference.LIGHT, result.theme)
        assertEquals(Revision(1), result.revision)
    }

    @Test
    fun staleRevisionIsRejectedAndChangesNothing(): Unit = blocking {
        val f = fixture()
        f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).success()
        val before = f.storeFile.readBytes()

        val behind = f.repo.update(patch(SettingChange.Theme(ThemePreference.LIGHT)), Revision(0), LiveToken)
        behind.failure(FailureCode.STALE_REVISION)
        val ahead = f.repo.update(patch(SettingChange.Theme(ThemePreference.LIGHT)), Revision(5), LiveToken)
        ahead.failure(FailureCode.STALE_REVISION)

        assertArrayEquals(before, f.storeFile.readBytes())
        val current = f.repo.read(LiveToken).success()
        assertEquals(Revision(1), current.revision)
        assertEquals(ThemePreference.DARK, current.theme)
    }

    @Test
    fun noOpPatchCommitsNothingAndKeepsRevisionAndTimestamp(): Unit = blocking {
        val f = fixture()
        val empty = f.repo.update(patch(), Revision(0), LiveToken).success()
        assertEquals(Revision(0), empty.revision)
        assertFalse("an empty patch on a never written store creates no file", f.storeFile.exists())

        val committed = f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).success()
        val before = f.storeFile.readBytes()
        f.clock.now = UtcTimestamp(1_760_999_999)

        val same = f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(1), LiveToken).success()
        assertEquals(committed, same)
        val roundTrip = f.repo.update(
            patch(SettingChange.Theme(ThemePreference.LIGHT), SettingChange.Theme(ThemePreference.DARK)),
            Revision(1),
            LiveToken,
        ).success()
        assertEquals(committed, roundTrip)
        assertArrayEquals(before, f.storeFile.readBytes())

        f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).failure(FailureCode.STALE_REVISION)
    }

    @Test
    fun invalidPatchesAreRejectedBeforeAnythingIsWritten(): Unit = blocking {
        val f = fixture()
        val negative = Knowledge.Known(DecimalAmount.exact("-1.00", DecimalUnit.Fiat("USD")))
        f.repo.update(patch(SettingChange.Budget(negative)), Revision(0), LiveToken).failure(FailureCode.INVALID_INPUT)
        val duplicates = SnapshotList.of(EntityId("p"), EntityId("p"))
        f.repo.update(patch(SettingChange.ProviderProfiles(duplicates)), Revision(0), LiveToken).failure(FailureCode.INVALID_INPUT)
        f.repo.update(patch(SettingChange.RevokeConsent(EntityId("missing"), false)), Revision(0), LiveToken)
            .failure(FailureCode.INVALID_INPUT)
        assertFalse(f.storeFile.exists())
        assertEquals(Revision(0), f.repo.read(LiveToken).success().revision)
    }

    @Test
    fun aRejectedChangeAbortsTheWholePatch(): Unit = blocking {
        val f = fixture()
        f.repo.update(
            patch(SettingChange.Theme(ThemePreference.DARK), SettingChange.RevokeConsent(EntityId("missing"), false)),
            Revision(0),
            LiveToken,
        ).failure(FailureCode.INVALID_INPUT)
        val current = f.repo.read(LiveToken).success()
        assertEquals(ThemePreference.SYSTEM, current.theme)
        assertEquals(Revision(0), current.revision)
    }

    @Test
    fun everyStoredVariantSurvivesARestart(): Unit = blocking {
        val f = fixture()
        val budgets = listOf(
            Knowledge.Known(DecimalAmount.exact("12.50", DecimalUnit.Fiat("EUR"))),
            Knowledge.Known(DecimalAmount.exact("3", DecimalUnit.Tix)),
            Knowledge.Known(DecimalAmount.exact("0.000001", DecimalUnit.Native(ProviderId("test-provider"), "pts"))),
            Knowledge.Absent("budget turned off"),
        )
        var revision = Revision(0)
        for (budget in budgets) {
            val snapshot = f.repo.update(patch(SettingChange.Budget(budget)), revision, LiveToken).success()
            revision = snapshot.revision
            assertEquals(snapshot, f.reopen().read(LiveToken).success())
            assertEquals(budget, snapshot.budget)
        }
        val known = budgets.first() as Knowledge.Known
        assertEquals("scale of 12.50 is preserved", 2, known.value.value.scale())

        val full = intent(
            id = "pack-de",
            kind = DownloadKind.LANGUAGE_PACK,
            language = LanguageTag("de"),
            packId = EntityId("pack-1"),
            checksum = ContentHash("sha256", "00ff"),
            expectedBytes = Knowledge.Known(123_456_789_012L),
        )
        val unknownSize = intent(id = "pack-fr", expectedBytes = Knowledge.Absent("not announced"))
        val record = f.repo.approve(full, LiveToken).success()
        f.repo.approve(unknownSize, LiveToken).success()
        val reopened = f.reopen().read(LiveToken).success()
        assertEquals(record, reopened.consents.first())
        assertEquals(full, reopened.consents.first().intent)
        assertEquals(unknownSize, reopened.consents[1].intent)
    }

    // ---- approve ----

    @Test
    fun approvePersistsTheConsentAndBumpsTheRevisionInTheSameCommit(): Unit = blocking {
        val f = fixture()
        val approvedAt = UtcTimestamp(1_760_000_200)
        f.clock.now = approvedAt
        val requested = intent()
        val record = f.repo.approve(requested, LiveToken).success()
        assertEquals(EntityId("consent-1"), record.id)
        assertEquals(requested, record.intent)
        assertEquals(approvedAt, record.approvedAt)
        assertEquals(null, record.revokedAt)
        assertTrue(record.explicitUserApproval)
        assertTrue(record.permits(requested))

        val snapshot = f.repo.read(LiveToken).success()
        assertEquals(Revision(1), snapshot.revision)
        assertEquals(SnapshotList.of(record), snapshot.consents)
        assertEquals(approvedAt, snapshot.updatedAt)

        val stored = SettingsStoreCodec.decode(f.storeFile.readBytes())
        assertEquals("revision and consent are in one committed file", Revision(1), stored.revision)
        assertEquals(listOf(StoredConsent(f.idFile.readText(), record)), stored.consents)

        assertEquals(snapshot, f.reopen().read(LiveToken).success())
        assertEquals("approve starts no job and creates no other file", setOf("settings.bin", "installation_id"), filesUnder(f.directory))
    }

    @Test
    fun approvingTheSameIntentAgainReturnsTheExistingRecordWithoutACommit(): Unit = blocking {
        val f = fixture()
        val first = f.repo.approve(intent(), LiveToken).success()
        f.clock.now = UtcTimestamp(1_760_500_000)
        val again = f.repo.approve(intent(), LiveToken).success()
        assertEquals(first, again)
        assertEquals(Revision(1), f.repo.read(LiveToken).success().revision)

        val other = f.repo.approve(intent(id = "intent-2"), LiveToken).success()
        assertNotEquals(first.id, other.id)
        assertEquals(Revision(2), f.repo.read(LiveToken).success().revision)
    }

    // ---- revoke ----

    @Test
    fun revokeRecordsTheRealTimeOnlyOnThatRecord(): Unit = blocking {
        val f = fixture()
        val approvedAt = UtcTimestamp(1_760_000_000, 100)
        val revokedAt = UtcTimestamp(1_760_003_600, 999_999_999)
        f.clock.now = approvedAt
        val kept = f.repo.approve(intent(id = "kept"), LiveToken).success()
        val revoking = f.repo.approve(intent(id = "revoked"), LiveToken).success()
        f.clock.now = revokedAt

        val after = f.repo.update(patch(SettingChange.RevokeConsent(revoking.id, removeDownloadedData = true)), Revision(2), LiveToken).success()
        assertEquals(Revision(3), after.revision)
        assertEquals(revokedAt, after.updatedAt)
        assertEquals(kept, after.consents[0])
        assertEquals(revoking.copy(revokedAt = revokedAt), after.consents[1])
        assertEquals(approvedAt, after.consents[1].approvedAt)
        assertFalse(after.consents[1].permits(intent(id = "revoked")))
        assertTrue(after.consents[0].permits(intent(id = "kept")))
        assertEquals(after, f.reopen().read(LiveToken).success())
    }

    @Test
    fun revokingAnAlreadyRevokedConsentKeepsItsOriginalTime(): Unit = blocking {
        val f = fixture()
        val record = f.repo.approve(intent(), LiveToken).success()
        val revokedAt = UtcTimestamp(1_760_100_000)
        f.clock.now = revokedAt
        f.repo.update(patch(SettingChange.RevokeConsent(record.id, false)), Revision(1), LiveToken).success()
        f.clock.now = UtcTimestamp(1_760_200_000)
        val again = f.repo.update(patch(SettingChange.RevokeConsent(record.id, true)), Revision(2), LiveToken).success()
        assertEquals(Revision(2), again.revision)
        assertEquals(revokedAt, again.consents.single().revokedAt)
    }

    @Test
    fun approvingARevokedIntentAgainCreatesANewEffectiveRecord(): Unit = blocking {
        val f = fixture()
        val first = f.repo.approve(intent(), LiveToken).success()
        f.repo.update(patch(SettingChange.RevokeConsent(first.id, false)), Revision(1), LiveToken).success()
        val second = f.repo.approve(intent(), LiveToken).success()
        assertNotEquals(first.id, second.id)
        assertEquals(null, second.revokedAt)
        val snapshot = f.repo.read(LiveToken).success()
        assertEquals(Revision(3), snapshot.revision)
        assertEquals(2, snapshot.consents.size)
        assertTrue(snapshot.consents.any { it.permits(intent()) })
    }

    // ---- restore never grants acquisition ----

    @Test
    fun consentsFromAnotherInstallationAreNotEffectiveAndAreNotRevoked(): Unit = blocking {
        val f = fixture()
        val record = f.repo.approve(intent(), LiveToken).success()
        val originalId = f.idFile.readText()
        f.repo.close()

        val foreignId = UUID.randomUUID().toString()
        assertNotEquals(originalId, foreignId)
        f.idFile.writeText(foreignId)
        val reopened = f.reopen()
        val snapshot = reopened.read(LiveToken).success()
        assertTrue("consent of another installation must not be reported", snapshot.consents.isEmpty())
        assertEquals("the settings themselves still restore", Revision(1), snapshot.revision)
        reopened.update(patch(SettingChange.RevokeConsent(record.id, false)), Revision(1), LiveToken)
            .failure(FailureCode.INVALID_INPUT)

        val fresh = reopened.approve(intent(), LiveToken).success()
        assertNotEquals("approval under the new installation is a new record", record.id, fresh.id)
        assertEquals(SnapshotList.of(fresh), reopened.read(LiveToken).success().consents)

        f.idFile.writeText(originalId)
        val back = f.reopen().read(LiveToken).success()
        assertEquals("the original record was neither revoked nor deleted", SnapshotList.of(record), back.consents)
        assertEquals(null, back.consents.single().revokedAt)
    }

    @Test
    fun aStoreRestoredOntoAnotherDeviceHasNoEffectiveConsent(): Unit = blocking {
        val source = fixture()
        source.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).success()
        source.repo.approve(intent(), LiveToken).success()
        source.repo.close()

        val target = fixture()
        writeStore(target, source.storeFile.readBytes())
        val restored = target.repo.read(LiveToken).success()
        assertEquals(ThemePreference.DARK, restored.theme)
        assertEquals(Revision(2), restored.revision)
        assertTrue(restored.consents.isEmpty())
        assertNotEquals(source.idFile.readText(), target.idFile.readText())
    }

    @Test
    fun theInstallationIdIsStableAndReplacedWhenItsContentIsNotAUuid(): Unit = blocking {
        val f = fixture()
        f.repo.read(LiveToken).success()
        val id = f.idFile.readText()
        UUID.fromString(id)
        f.reopen().read(LiveToken).success()
        assertEquals(id, f.idFile.readText())

        val g = fixture()
        g.idFile.parentFile!!.mkdirs()
        g.idFile.writeText("not a uuid")
        g.repo.read(LiveToken).success()
        UUID.fromString(g.idFile.readText())
    }

    @Test
    fun anUnreadableInstallationIdIsAPersistenceFailureNotAGrant(): Unit = blocking {
        val f = fixture()
        f.idFile.mkdirs()
        f.repo.read(LiveToken).failure(FailureCode.PERSISTENCE)
        f.repo.approve(intent(), LiveToken).failure(FailureCode.PERSISTENCE)
        assertFalse(f.storeFile.exists())
    }

    // ---- stored version and corruption ----

    @Test
    fun anUnknownNewerStoredVersionIsATypedFailureAndTheFileIsNeverReset(): Unit = blocking {
        for (version in listOf(2, 99, Int.MAX_VALUE)) {
            val f = fixture()
            val bytes = bytesWithVersion(version)
            writeStore(f, bytes)

            val failure = f.repo.read(LiveToken).failure(FailureCode.CORRUPT_DATA)
            assertTrue(failure.message, failure.message.contains("newer"))
            assertTrue(failure.evidence.single().description.contains("stored-version=$version"))
            f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).failure(FailureCode.CORRUPT_DATA)
            f.repo.approve(intent(), LiveToken).failure(FailureCode.CORRUPT_DATA)
            assertArrayEquals(bytes, f.storeFile.readBytes())

            f.reopen().read(LiveToken).failure(FailureCode.CORRUPT_DATA)
            assertArrayEquals("a restart does not reset the store either", bytes, f.storeFile.readBytes())
        }
    }

    @Test
    fun aDamagedStoreIsATypedFailureAndIsNeverReplacedWithDefaults(): Unit = blocking {
        val good = SettingsStoreCodec.encode(StoredSettings.INITIAL.copy(theme = ThemePreference.DARK))
        val flipped = good.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() }
        val damaged = listOf(
            "empty" to ByteArray(0),
            "truncated" to good.copyOf(good.size - 1),
            "checksum" to flipped,
            "garbage" to "this is not a settings store at all".toByteArray(),
            "version zero" to bytesWithVersion(0),
            "negative version" to bytesWithVersion(-1),
        )
        for ((label, bytes) in damaged) {
            val f = fixture()
            writeStore(f, bytes)
            f.repo.read(LiveToken).failure(FailureCode.CORRUPT_DATA)
            f.repo.update(patch(SettingChange.Theme(ThemePreference.LIGHT)), Revision(0), LiveToken).failure(FailureCode.CORRUPT_DATA)
            assertArrayEquals("$label store must stay as found", bytes, f.storeFile.readBytes())
        }
    }

    @Test
    fun anExhaustedRevisionFailsTheEditInsteadOfWrapping(): Unit = blocking {
        val f = fixture()
        val last = Revision(Long.MAX_VALUE)
        writeStore(f, SettingsStoreCodec.encode(StoredSettings.INITIAL.copy(revision = last)))
        assertEquals(last, f.repo.read(LiveToken).success().revision)
        f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), last, LiveToken).failure(FailureCode.PERSISTENCE)
        f.repo.approve(intent(), LiveToken).failure(FailureCode.PERSISTENCE)
        f.repo.update(patch(), last, LiveToken).success()
        assertEquals(last, f.repo.read(LiveToken).success().revision)
    }

    // ---- concurrency ----

    @Test
    fun concurrentUpdatesWithTheSameExpectedRevisionHaveExactlyOneWinner(): Unit = blocking {
        val f = fixture()
        var revision = Revision(0)
        repeat(5) { round ->
            val results = (0 until 16).map { index ->
                async(Dispatchers.Default) {
                    index to f.repo.update(
                        patch(SettingChange.DataLanguage(LanguageTag("r$round-c$index"))),
                        revision,
                        LiveToken,
                    )
                }
            }.awaitAll()
            val winners = results.filter { it.second is Outcome.Success }
            assertEquals("round $round", 1, winners.size)
            results.filter { it !== winners.single() }.forEach { it.second.failure(FailureCode.STALE_REVISION) }
            val state = f.repo.read(LiveToken).success()
            assertEquals(revision.next(), state.revision)
            assertEquals(LanguageTag("r$round-c${winners.single().first}"), state.dataLanguage)
            revision = state.revision
        }
    }

    @Test
    fun concurrentApprovalsAllCommitAndEachIncrementsOnce(): Unit = blocking {
        val f = fixture()
        (0 until 12).map { index -> async(Dispatchers.Default) { f.repo.approve(intent(id = "intent-$index"), LiveToken).success() } }.awaitAll()
        val snapshot = f.repo.read(LiveToken).success()
        assertEquals(Revision(12), snapshot.revision)
        assertEquals(12, snapshot.consents.map { it.id }.toSet().size)
    }

    // ---- cancellation ----

    @Test
    fun aCancelledTokenIsRethrownAndNothingIsWritten(): Unit = blocking {
        val f = fixture()
        assertCancelled { f.repo.read(CancelledToken) }
        assertCancelled { f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), CancelledToken) }
        assertCancelled { f.repo.approve(intent(), CancelledToken) }
        assertFalse(f.storeFile.exists())
        val healthy = f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).success()
        assertEquals(Revision(1), healthy.revision)
    }

    @Test
    fun cancellationAtEachCheckpointNeverLeavesAPartialCommit(): Unit = blocking {
        for (operation in listOf("update", "approve")) {
            for (cancelOnCall in 1..6) {
                val f = fixture()
                val token = CancelOnCallToken(cancelOnCall)
                var thrown = false
                var succeeded = false
                try {
                    if (operation == "update") {
                        f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), token).success()
                    } else {
                        f.repo.approve(intent(), token).success()
                    }
                    succeeded = true
                } catch (cancelled: CancellationException) {
                    thrown = true
                }
                // A failure Outcome would have failed success() above; only Success or CancellationException get here.

                val after = f.repo.read(LiveToken).success()
                assertTrue("$operation call $cancelOnCall: revision is 0 or 1", after.revision == Revision(0) || after.revision == Revision(1))
                if (after.revision == Revision(0)) {
                    assertFalse("$operation call $cancelOnCall: nothing committed means no file", f.storeFile.exists())
                    assertTrue(after.consents.isEmpty())
                    assertEquals(ThemePreference.SYSTEM, after.theme)
                } else if (operation == "update") {
                    assertEquals(ThemePreference.DARK, after.theme)
                } else {
                    assertEquals(1, after.consents.size)
                }
                if (cancelOnCall <= 3) {
                    assertTrue("$operation call $cancelOnCall: cancelled before the commit", thrown)
                    assertEquals(Revision(0), after.revision)
                }
                if (succeeded) assertEquals(Revision(1), after.revision)
                f.repo.update(patch(SettingChange.Theme(ThemePreference.LIGHT)), after.revision, LiveToken).success()
            }
        }
    }

    @Test
    fun cancellingTheCallerWhileTheTransactionRunsAbortsTheCommit(): Unit = blocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val gate = object : Clock {
            override fun utcNow(): UtcTimestamp {
                entered.countDown()
                release.await(30, TimeUnit.SECONDS)
                return UtcTimestamp(1_760_000_000)
            }

            override fun monotonicNow(): Duration = System.nanoTime().nanoseconds
        }
        val directory = temporary.newFolder()
        val storeFile = File(directory, "gated/settings.bin")
        val repo = DataStoreSettingsRepository.create(storeFile, File(directory, "gated/id"), com.architectpsg.commandlibrary.core.common.ConsentCoordinator(), gate, SequenceIds())
        try {
            val job = launch(Dispatchers.Default) {
                repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), CoroutineCancelToken(coroutineContext))
            }
            withContext(Dispatchers.IO) { assertTrue(entered.await(30, TimeUnit.SECONDS)) }
            job.cancel()
            release.countDown()
            job.join()
            assertTrue("the caller's coroutine ends cancelled", job.isCancelled)

            assertEquals(Revision(0), repo.read(LiveToken).success().revision)
            assertFalse("the aborted transaction wrote nothing", storeFile.exists())
            repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).success()
        } finally {
            release.countDown()
            repo.close()
        }
    }

    // ---- lifecycle ----

    @Test
    fun aClosedRepositoryReportsAFailureInsteadOfCancellingItsCaller(): Unit = blocking {
        val f = fixture()
        f.repo.read(LiveToken).success()
        f.repo.close()
        f.repo.read(LiveToken).failure(FailureCode.PERSISTENCE)
        f.repo.update(patch(SettingChange.Theme(ThemePreference.DARK)), Revision(0), LiveToken).failure(FailureCode.PERSISTENCE)
        f.repo.approve(intent(), LiveToken).failure(FailureCode.PERSISTENCE)
    }

    @Test
    fun aSecondLiveInstanceOnTheSameFileIsAnInternalFailure(): Unit = blocking {
        val f = fixture()
        f.repo.read(LiveToken).success()
        val second = f.open()
        second.read(LiveToken).failure(FailureCode.INTERNAL)
        assertEquals(Revision(0), f.repo.read(LiveToken).success().revision)
    }

    private fun bytesWithVersion(version: Int): ByteArray {
        val bytes = SettingsStoreCodec.encode(StoredSettings.INITIAL)
        java.nio.ByteBuffer.wrap(bytes, 4, 4).putInt(version)
        return bytes
    }
    @Test
    fun consentRevocationSharesAcquisitionGuardAndReadDoesNotDeadlock(): Unit = blocking {
        val f = fixture()
        val approved = f.repo.approve(intent(), LiveToken).success()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val owner = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            f.consentCoordinator.withExclusive(LiveToken) {
                assertTrue(f.repo.read(LiveToken).success().consents.any { it.id == approved.id })
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val revision = f.repo.read(LiveToken).success().revision
        val revoke = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            f.repo.update(patch(SettingChange.RevokeConsent(approved.id, false)), revision, LiveToken)
        }
        assertFalse("revocation waits for bounded acquisition critical section", revoke.isCompleted)
        release.complete(Unit)
        kotlinx.coroutines.withTimeout(2000) { owner.join(); revoke.await().success() }
        f.consentCoordinator.withExclusive(LiveToken) {
            assertFalse(f.repo.read(LiveToken).success().consents.single { it.id == approved.id }.permits(approved.intent))
        }
    }

    @Test
    fun cancellationDuringConsentWriteKeepsGuardUntilActorCommitSettles(): Unit = blocking {
        val directory = temporary.newFolder()
        val coordinator = com.architectpsg.commandlibrary.core.common.ConsentCoordinator()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val blockWrite = java.util.concurrent.atomic.AtomicBoolean(false)
        val serializer = object : androidx.datastore.core.Serializer<StoredSettings> {
            override val defaultValue = SettingsStoreSerializer.defaultValue
            override suspend fun readFrom(input: java.io.InputStream) = SettingsStoreSerializer.readFrom(input)
            override suspend fun writeTo(t: StoredSettings, output: java.io.OutputStream) {
                if (blockWrite.get()) { entered.complete(Unit); release.await() }
                SettingsStoreSerializer.writeTo(t, output)
            }
        }
        val repo = DataStoreSettingsRepository.createWithSerializer(
            File(directory, "settings.bin"), File(directory, "installation-id"), coordinator,
            FakeClock(), SequenceIds(), serializer,
        )
        try {
            val approved = repo.approve(intent(), LiveToken).success()
            val revision = repo.read(LiveToken).success().revision
            blockWrite.set(true)
            val revoke = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                repo.update(patch(SettingChange.RevokeConsent(approved.id, false)), revision, LiveToken)
            }
            kotlinx.coroutines.withTimeout(2000) { entered.await() }
            revoke.cancel()
            kotlinx.coroutines.yield()
            assertFalse("caller cancellation must await outstanding actor write", revoke.isCompleted)
            var dispatched = false
            val dispatch = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                coordinator.withExclusive(LiveToken) {
                    dispatched = true
                    repo.read(LiveToken).success()
                }
            }
            assertFalse(dispatch.isCompleted)
            assertFalse(dispatched)
            release.complete(Unit)
            val final = kotlinx.coroutines.withTimeout(2000) { revoke.join(); dispatch.await() }
            assertTrue(revoke.isCancelled)
            assertTrue(dispatched)
            assertFalse(final.consents.single { it.id == approved.id }.permits(approved.intent))
        } finally {
            release.complete(Unit)
            repo.close()
        }
    }

}
