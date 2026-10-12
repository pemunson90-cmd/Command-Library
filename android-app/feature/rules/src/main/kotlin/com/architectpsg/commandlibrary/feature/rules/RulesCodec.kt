package com.architectpsg.commandlibrary.feature.rules

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.*

/** Versioned, per-record payloads; all Knowledge states and evidence survive reopening. */
internal object RulesCodec {
    private const val MAX_PAYLOAD = 2 * 1024 * 1024
    fun encode(value: JsonObject): String = value.apply { addProperty("schema", 1) }.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD) }
    fun decode(value: String): JsonObject {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD)
        return JsonParser.parseString(value).asJsonObject.also { require(integer(it.get("schema")) == 1L) }
    }
    fun source(v: LocalRulesImportSource): JsonObject = obj {
        s("uri", v.contentUri.value); s("name", v.displayName); s("provider", v.provider.value); s("language", v.language.value)
        add("version", knowledge(v.version) { JsonPrimitive(it) }); add("effective", knowledge(v.effectiveAt, ::date))
        add("date", knowledge(v.sourceDate, ::date)); add("source", knowledge(v.sourceUri) { JsonPrimitive(it.value) })
        add("notice", knowledge(v.notice, ::notice)); add("hash", knowledge(v.expectedChecksum, ::hash)); add("bytes", knowledge(v.expectedBytes) { JsonPrimitive(it) })
    }
    fun source(v: JsonObject): LocalRulesImportSource = LocalRulesImportSource(ResourceUri(v.t("uri")), v.t("name"), ProviderId(v.t("provider")), LanguageTag(v.t("language")),
        read(v["version"]) { string(it) }, read(v["effective"], ::date), read(v["date"], ::date), read(v["source"]) { ResourceUri(string(it)) },
        read(v["notice"], ::notice), read(v["hash"], ::hash), read(v["bytes"]) { integer(it) })
    fun provenance(v: Provenance): JsonObject = obj {
        s("provider", v.provider.value); v.source?.let { s("source", it.value) }; add("version", knowledge(v.version) { JsonPrimitive(it) })
        add("date", knowledge(v.sourceDate, ::date)); add("observed", date(v.fetchedAt)); add("effective", knowledge(v.effectiveAt, ::date))
        v.generation?.let { s("generation", it.value) }; v.checksum?.let { add("hash", hash(it)) }; v.notice?.let { add("notice", notice(it)) }
    }
    fun provenance(v: JsonObject): Provenance = Provenance(ProviderId(v.t("provider")), v.get("source")?.let { ResourceUri(string(it)) }, read(v["version"]) { string(it) },
        read(v["date"], ::date), date(v["observed"]), read(v["effective"], ::date), v.get("generation")?.let { GenerationId(string(it)) }, v.get("hash")?.let(::hash), v.get("notice")?.let(::notice))
    fun entry(v: RuleEntry): JsonObject = obj {
        add("id", id(v.id)); s("corpus", v.corpusId.value); s("kind", v.kind.name); add("number", knowledge(v.number) { JsonPrimitive(it) })
        add("title", knowledge(v.title) { JsonPrimitive(it) }); s("text", v.text); addProperty("ordinal", v.ordinal)
        add("references", JsonArray().apply { v.references.forEach { add(reference(it)) } }); add("provenance", provenance(v.provenance))
    }
    fun entry(v: JsonObject): RuleEntry = RuleEntry(id(v["id"]), GenerationId(v.t("corpus")), RuleEntryKind.valueOf(v.t("kind")), read(v["number"]) { string(it) },
        read(v["title"]) { string(it) }, v.t("text"), integer(v["ordinal"]), SnapshotList.copyOf(v.getAsJsonArray("references").map(::reference)), provenance(v.getAsJsonObject("provenance")))
    fun reference(v: RuleCrossReference): JsonObject = obj { s("target", v.rawTarget); add("resolved", knowledge(v.resolved, ::id)); add("evidence", evidence(v.evidence)) }
    fun reference(v: JsonElement): RuleCrossReference = v.asJsonObject.let { RuleCrossReference(it.t("target"), read(it["resolved"], ::id), evidence(it["evidence"])) }
    fun attempt(v: LocalRulesImportAttempt): JsonObject = obj { s("id", v.id.value); add("source", source(v.source)); s("phase", v.phase.name); add("started", date(v.startedAt)); add("finished", knowledge(v.finishedAt, ::date)); v.failure?.let { add("failure", failure(it)) } }
    fun attempt(v: JsonObject): LocalRulesImportAttempt = LocalRulesImportAttempt(EntityId(v.t("id")), source(v.getAsJsonObject("source")), JobPhase.valueOf(v.t("phase")), date(v["started"]), read(v["finished"], ::date), v.get("failure")?.let { failure(it.asJsonObject) })
    fun failure(v: DomainFailure): JsonObject = obj { s("code", v.code.name); s("message", v.message); add("evidence", evidence(v.evidence)) }
    fun failure(v: JsonObject): DomainFailure = DomainFailure(FailureCode.valueOf(v.t("code")), v.t("message"), evidence(v["evidence"]))
    private fun id(v: RuleId): JsonObject = obj { s("provider", v.source.value); s("raw", v.raw) }
    private fun id(v: JsonElement): RuleId = v.asJsonObject.let { RuleId(ProviderId(it.t("provider")), it.t("raw")) }
    private fun hash(v: ContentHash): JsonObject = obj { s("algorithm", v.algorithm); s("hex", v.hex) }
    private fun hash(v: JsonElement): ContentHash = v.asJsonObject.let { ContentHash(it.t("algorithm"), it.t("hex")) }
    private fun date(v: UtcTimestamp): JsonObject = obj { addProperty("seconds", v.epochSeconds); addProperty("nanos", v.nanoAdjustment) }
    private fun date(v: JsonElement): UtcTimestamp = v.asJsonObject.let { UtcTimestamp(integer(it["seconds"]), integer(it["nanos"]).also { n -> require(n in 0..999_999_999) }.toInt()) }
    private fun notice(v: SourceNotice): JsonObject = obj { s("provider", v.provider.value); s("title", v.title); s("attribution", v.attribution); v.uri?.let { s("uri", it.value) }; add("license", knowledge(v.license) { JsonPrimitive(it) }) }
    private fun notice(v: JsonElement): SourceNotice = v.asJsonObject.let { SourceNotice(ProviderId(it.t("provider")), it.t("title"), read(it["license"]) { string(it) }, it.t("attribution"), it.get("uri")?.let { ResourceUri(string(it)) }) }
    private fun evidence(v: SnapshotList<Evidence>): JsonArray = JsonArray().apply { v.forEach { e -> add(obj { s("description", e.description); e.source?.let { s("source", it.value) }; e.recordedAt?.let { add("at", date(it)) } }) } }
    private fun evidence(v: JsonElement): SnapshotList<Evidence> = SnapshotList.copyOf(v.asJsonArray.map { e -> e.asJsonObject.let { Evidence(it.t("description"), it.get("source")?.let { ResourceUri(string(it)) }, it.get("at")?.let(::date)) } })
    private fun <T> knowledge(v: Knowledge<T>, encode: (T) -> JsonElement): JsonObject = obj { when (v) {
        is Knowledge.Known -> { s("state", "known"); add("value", encode(v.value)) }
        is Knowledge.Absent -> { s("state", "absent"); s("reason", v.reason) }
        is Knowledge.Unknown -> { s("state", "unknown"); s("reason", v.reason); add("evidence", evidence(v.evidence)) }
    } }
    private fun <T> read(v: JsonElement, decode: (JsonElement) -> T): Knowledge<T> = v.asJsonObject.let { when (it.t("state")) {
        "known" -> Knowledge.Known(decode(it["value"])); "absent" -> Knowledge.Absent(it.t("reason")); "unknown" -> Knowledge.Unknown(it.t("reason"), evidence(it["evidence"])); else -> error("Unknown Knowledge state")
    } }
    fun integer(v: JsonElement): Long {
        require(v.isJsonPrimitive && v.asJsonPrimitive.isNumber)
        val text = v.asJsonPrimitive.toString()
        require(text.matches(Regex("-?(0|[1-9][0-9]*)")))
        return text.toLong()
    }
    fun string(v: JsonElement): String { require(v.isJsonPrimitive && v.asJsonPrimitive.isString); return v.asString }
    private fun obj(action: JsonObject.() -> Unit): JsonObject = JsonObject().apply(action)
    private fun JsonObject.s(k: String, v: String) { addProperty(k, v) }
    private fun JsonObject.t(k: String): String = string(get(k))
}
