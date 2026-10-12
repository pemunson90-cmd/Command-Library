package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CachePolicy
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.CardFace
import com.architectpsg.commandlibrary.core.model.CardRecord
import com.architectpsg.commandlibrary.core.model.CardStats
import com.architectpsg.commandlibrary.core.model.CatalogCounts
import com.architectpsg.commandlibrary.core.model.CatalogGeneration
import com.architectpsg.commandlibrary.core.model.CatalogImport
import com.architectpsg.commandlibrary.core.model.CatalogImportResult
import com.architectpsg.commandlibrary.core.model.CatalogImportSource
import com.architectpsg.commandlibrary.core.model.CatalogImportVerification
import com.architectpsg.commandlibrary.core.model.CatalogLookup
import com.architectpsg.commandlibrary.core.model.CatalogStatus
import com.architectpsg.commandlibrary.core.model.ChecksumVerification
import com.architectpsg.commandlibrary.core.model.Color
import com.architectpsg.commandlibrary.core.model.ConsentRecord
import com.architectpsg.commandlibrary.core.model.ContentHash
import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.DownloadIntent
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.FaceIdentity
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.GenerationId
import com.architectpsg.commandlibrary.core.model.GenerationStatus
import com.architectpsg.commandlibrary.core.model.JobPhase
import com.architectpsg.commandlibrary.core.model.JobProgress
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.core.model.LocalCatalogStatus
import com.architectpsg.commandlibrary.core.model.OracleId
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.Page
import com.architectpsg.commandlibrary.core.model.PageRequest
import com.architectpsg.commandlibrary.core.model.Provenance
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.SettingChange
import com.architectpsg.commandlibrary.core.model.SettingsPatch
import com.architectpsg.commandlibrary.core.model.SettingsRepository
import com.architectpsg.commandlibrary.core.model.SettingsSnapshot
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.SnapshotReference
import com.architectpsg.commandlibrary.core.model.StorageMode
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.core.model.TypeLine
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import com.architectpsg.commandlibrary.feature.shell.document.DocumentDescription
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.document.DocumentSource
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

/*
 * Test-only, explicitly synthetic port doubles. They exist to drive the real shell controllers and
 * Compose UI in tests; none of them is a product adapter and none is reachable from main sources.
 * Card names, identifiers and timestamps below are invented test values, not catalog data.
 */

val SYNTHETIC_PROVIDER: ProviderId = ProviderId("scryfall")
val SYNTHETIC_TIME: UtcTimestamp = UtcTimestamp(1_760_000_000)

fun syntheticCard(name: String, generation: GenerationId?, text: String = "Synthetic rules text for $name."): CardRecord {
    val oracle = OracleId(SYNTHETIC_PROVIDER, "synthetic-oracle-" + name.lowercase().replace(' ', '-'))
    val types = TypeLine("Creature - Synthetic", SnapshotList.empty(), SnapshotList.of("Creature"), SnapshotList.of("Synthetic"))
    val stats = CardStats(
        power = Knowledge.Known("2"),
        toughness = Knowledge.Known("3"),
        loyalty = Knowledge.Absent("loyalty not supplied"),
        defense = Knowledge.Absent("defense not supplied"),
    )
    return CardRecord(
        oracleId = oracle,
        name = name,
        originalText = Knowledge.Known(text),
        manaCost = Knowledge.Known("{1}{U}"),
        manaValue = Knowledge.Known(BigDecimal("2")),
        colors = Knowledge.Known(SnapshotList.of(Color.BLUE)),
        colorIdentity = Knowledge.Known(SnapshotList.of(Color.BLUE)),
        types = Knowledge.Known(types),
        stats = stats,
        keywords = Knowledge.Known(SnapshotList.empty()),
        layout = Knowledge.Known("normal"),
        reserved = Knowledge.Known(false),
        normalizedSearchText = name.lowercase(),
        language = LanguageTag.ENGLISH,
        faces = SnapshotList.of(
            CardFace(
                identity = FaceIdentity(oracle, 0, Knowledge.Absent("face id not supplied")),
                name = name,
                text = Knowledge.Known(text),
                manaCost = Knowledge.Known("{1}{U}"),
                types = Knowledge.Known(types),
                stats = stats,
                relationships = SnapshotList.empty(),
            ),
        ),
        providerIdentities = SnapshotList.empty(),
        provenance = syntheticProvenance(generation),
        revision = null,
    )
}

fun syntheticProvenance(generation: GenerationId?): Provenance = Provenance(
    provider = SYNTHETIC_PROVIDER,
    source = null,
    version = Knowledge.Unknown("synthetic", SnapshotList.empty()),
    sourceDate = Knowledge.Unknown("synthetic source date not supplied", SnapshotList.empty()),
    fetchedAt = SYNTHETIC_TIME,
    effectiveAt = Knowledge.Unknown("synthetic", SnapshotList.empty()),
    generation = generation,
    checksum = ContentHash("SHA-256", "ab".repeat(32)),
    notice = null,
)

fun failure(code: FailureCode): Outcome.Failure = Outcome.Failure(DomainFailure(code, "synthetic failure", SnapshotList.empty()))

/**
 * Synthetic in-memory catalog implementing the three local catalog ports consistently: an import
 * that reads its whole stream activates a new generation; cancellation or failure keeps the old one;
 * lookup pages are pinned to one generation and a cursor from an older one is rejected as stale.
 */
class SyntheticCatalog(initialNames: List<String> = emptyList()) : CatalogLookup, CatalogStatus, CatalogImport {
    data class Generation(val id: GenerationId, val names: List<String>, val bytes: Long)

    private val generationCounter = AtomicInteger(0)

    @Volatile
    var active: Generation? = if (initialNames.isEmpty()) null else newGeneration(initialNames, bytes = 0)

    @Volatile
    var statusOverride: Outcome<LocalCatalogStatus>? = null

    @Volatile
    var lookupOverride: Outcome<Page<CardRecord>>? = null

    /** When set, lookup suspends until it completes. */
    @Volatile
    var lookupGate: CompletableDeferred<Unit>? = null

    @Volatile
    var importOutcomeOverride: Outcome<CatalogImportResult>? = null

    /** When set, import suspends after its first chunk until the gate completes. */
    @Volatile
    var importGate: CompletableDeferred<Unit>? = null

    @Volatile
    var namesForNextImport: List<String> = listOf("Synthetic Imported Card")

    val statusCalls = AtomicInteger(0)
    val lookupRequests: MutableList<Pair<String, PageRequest>> = java.util.Collections.synchronizedList(mutableListOf())
    val lookupTokens: MutableList<CancelToken> = java.util.Collections.synchronizedList(mutableListOf())
    val importCalls = AtomicInteger(0)

    @Volatile
    var lastSource: CatalogImportSource? = null

    @Volatile
    var lastImportToken: CancelToken? = null

    @Volatile
    var bytesReceived: Long = 0

    @Volatile
    var firstChunk: CompletableDeferred<Unit> = CompletableDeferred()

    val importCancelled = AtomicBoolean(false)

    fun newGeneration(names: List<String>, bytes: Long): Generation =
        Generation(GenerationId("synthetic-generation-${generationCounter.incrementAndGet()}"), names.sorted(), bytes)

    fun localStatus(): LocalCatalogStatus {
        val current = active
        val generations = if (current == null) SnapshotList.empty() else SnapshotList.of(catalogGeneration(current))
        return LocalCatalogStatus(
            mode = StorageMode.FULL,
            defaultLanguage = LanguageTag.ENGLISH,
            activeGenerations = generations,
            source = if (current == null) Knowledge.Absent("No catalog installed") else Knowledge.Known(SYNTHETIC_PROVIDER),
            sourceDate = Knowledge.Unknown("synthetic source date not supplied", SnapshotList.empty()),
            fetchedAt = if (current == null) Knowledge.Unknown("No import observed", SnapshotList.empty()) else Knowledge.Known(SYNTHETIC_TIME),
            installedLanguages = if (current == null) SnapshotList.empty() else SnapshotList.of(LanguageTag.ENGLISH),
            initialized = current != null,
        )
    }

    fun catalogGeneration(generation: Generation): CatalogGeneration = CatalogGeneration(
        id = generation.id,
        provenance = syntheticProvenance(generation.id),
        languages = SnapshotList.of(LanguageTag.ENGLISH),
        format = "synthetic-json-array",
        counts = CatalogCounts(generation.names.size.toLong(), generation.names.size.toLong(), generation.names.size.toLong(), 0),
        status = GenerationStatus.ACTIVE,
    )

    override suspend fun status(cancel: CancelToken): Outcome<LocalCatalogStatus> {
        statusCalls.incrementAndGet()
        return statusOverride ?: Outcome.Success(localStatus())
    }

    override suspend fun lookupName(input: String, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> {
        lookupRequests += input to page
        lookupTokens += cancel
        lookupGate?.await()
        lookupOverride?.let { return it }
        val current = active
        val reference = SnapshotReference(SYNTHETIC_PROVIDER, current?.id, null, LanguageTag.ENGLISH)
        if (current == null) return Outcome.Success(Page(SnapshotList.empty(), null, reference))
        val start = when (val cursor = page.cursor) {
            null -> 0
            else -> {
                val (generation, index) = cursor.split('|')
                if (generation != current.id.value) return failure(FailureCode.STALE_GENERATION)
                index.toInt()
            }
        }
        val matches = current.names.filter { it.contains(input, ignoreCase = true) }
        val items = matches.drop(start).take(page.limit)
        val next = if (start + items.size < matches.size) "${current.id.value}|${start + items.size}" else null
        return Outcome.Success(Page(SnapshotList.copyOf(items.map { syntheticCard(it, current.id) }), next, reference))
    }

    override suspend fun `import`(
        source: CatalogImportSource,
        chunks: Flow<ByteChunk>,
        onProgress: (JobProgress) -> Unit,
        cancel: CancelToken,
    ): Outcome<CatalogImportResult> {
        importCalls.incrementAndGet()
        lastSource = source
        lastImportToken = cancel
        bytesReceived = 0
        var expectedSequence = 0L
        var finalSeen = false
        try {
            chunks.collect { chunk ->
                check(chunk.sequence == expectedSequence && chunk.offset == bytesReceived && !finalSeen) { "noncontiguous" }
                expectedSequence++
                bytesReceived += chunk.bytes.size
                finalSeen = chunk.final
                onProgress(JobProgress(EntityId("synthetic-job"), JobPhase.STAGING, expectedSequence, Knowledge.Unknown("synthetic", SnapshotList.empty()), "synthetic", null))
                firstChunk.complete(Unit)
                importGate?.await()
            }
        } catch (cancelled: CancellationException) {
            importCancelled.set(true)
            throw cancelled
        } catch (_: Exception) {
            return failure(FailureCode.INVALID_INPUT)
        }
        importOutcomeOverride?.let { return it }
        if (!finalSeen) return failure(FailureCode.INVALID_INPUT)
        val generation = newGeneration(namesForNextImport, bytesReceived)
        active = generation
        val counts = CatalogCounts(generation.names.size.toLong(), generation.names.size.toLong(), generation.names.size.toLong(), 0)
        return Outcome.Success(
            CatalogImportResult(
                generation = catalogGeneration(generation),
                counts = counts,
                provenance = syntheticProvenance(generation.id),
                verification = CatalogImportVerification(bytesReceived, ContentHash("SHA-256", "cd".repeat(32)), ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION, true, 0),
                activatedAt = SYNTHETIC_TIME,
            ),
        )
    }
}

/** Synthetic in-memory settings honoring expected-revision semantics. */
class SyntheticSettings(theme: ThemePreference = ThemePreference.SYSTEM) : SettingsRepository {
    @Volatile
    var stored: SettingsSnapshot = snapshot(Revision(0), theme)

    @Volatile
    var readOverride: Outcome<SettingsSnapshot>? = null

    @Volatile
    var updateOverride: Outcome<SettingsSnapshot>? = null

    @Volatile
    var updateGate: CompletableDeferred<Unit>? = null

    val updates: MutableList<Pair<SettingsPatch, Revision>> = java.util.Collections.synchronizedList(mutableListOf())
    val reads = AtomicInteger(0)

    /** Simulates another writer committing a change. */
    fun changeElsewhere(theme: ThemePreference) {
        stored = snapshot(stored.revision.next(), theme)
    }

    override suspend fun read(cancel: CancelToken): Outcome<SettingsSnapshot> {
        reads.incrementAndGet()
        return readOverride ?: Outcome.Success(stored)
    }

    override suspend fun update(patch: SettingsPatch, expected: Revision, cancel: CancelToken): Outcome<SettingsSnapshot> {
        updates += patch to expected
        updateGate?.await()
        updateOverride?.let { return it }
        if (expected != stored.revision) return failure(FailureCode.STALE_REVISION)
        var theme = stored.theme
        for (change in patch.changes) {
            if (change is SettingChange.Theme) theme = change.theme else return failure(FailureCode.INVALID_INPUT)
        }
        stored = snapshot(stored.revision.next(), theme)
        return Outcome.Success(stored)
    }

    override suspend fun approve(intent: DownloadIntent, cancel: CancelToken): Outcome<ConsentRecord> =
        failure(FailureCode.INVALID_INPUT)

    private fun snapshot(revision: Revision, theme: ThemePreference): SettingsSnapshot = SettingsSnapshot(
        revision = revision,
        theme = theme,
        cache = CachePolicy(imageBytes = 0, retainRulings = true, wifiOnlyDownloads = true),
        budget = Knowledge.Absent("synthetic"),
        providerProfileIds = SnapshotList.empty(),
        sourceNotices = SnapshotList.empty(),
        consents = SnapshotList.empty(),
        updatedAt = SYNTHETIC_TIME,
    )
}

/** Synthetic documents served from memory or from a supplied stream. */
internal class MemoryDocuments : DocumentSource {
    class Entry(
        val name: String?,
        val reportedBytes: Long?,
        val open: () -> InputStream,
        val describeError: Exception? = null,
    )

    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()
    val opened = AtomicInteger(0)

    fun put(uri: String, entry: Entry) {
        entries[uri] = entry
    }

    fun putBytes(uri: String, name: String?, bytes: ByteArray, reportedBytes: Long? = bytes.size.toLong()) =
        put(uri, Entry(name, reportedBytes, { ByteArrayInputStream(bytes) }))

    override fun describe(ref: DocumentRef): DocumentDescription {
        val entry = entries[ref.uri] ?: throw FileNotFoundException(ref.uri)
        entry.describeError?.let { throw it }
        return DocumentDescription(entry.name, entry.reportedBytes)
    }

    override fun open(ref: DocumentRef): InputStream {
        val entry = entries[ref.uri] ?: throw FileNotFoundException(ref.uri)
        opened.incrementAndGet()
        return entry.open()
    }
}

/** Synthetic bytes; contents are irrelevant to the synthetic importer. */
fun syntheticBytes(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }
