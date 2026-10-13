package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.*
import com.google.gson.internal.Streams
import com.google.gson.stream.JsonWriter
import java.io.Writer

/** Persistence structure only: does not authorize a source, verify consent, or perform IO. */
internal object AcquisitionPayloadCodec {
    const val VERSION = 1
    const val MAX_BYTES = 1024 * 1024
    const val MAX_NESTING = 32

    fun encodePlan(plan: DownloadPlan): String = guarded { encode("plan", planJson(plan)).also { decodePlan(it) } }
    fun decodePlan(payload: String): DownloadPlan = guarded { readPlan(envelope(payload, "plan")) }
    fun encodeArtifact(artifact: VerifiedArtifact): String = guarded { encode("artifact", artifactJson(artifact)).also { decodeArtifact(it) } }
    fun decodeArtifact(payload: String): VerifiedArtifact = guarded { readArtifact(envelope(payload, "artifact")) }

    /** Full importer source persistence only; callers must still validate/pin downloaded artifacts. */
    fun encodeCatalogSource(source: CatalogBulkImportSource): String = guarded {
        encode("catalogSource", catalogSourceJson(source)).also { decodeCatalogSource(it) }
    }
    fun decodeCatalogSource(payload: String): CatalogBulkImportSource = guarded {
        val v = envelope(payload, "catalogSource").fields("contentUri", "displayName", "provider", "language",
            "sourceDate", "sourceUri", "expectedChecksum", "expectedBytes", "notice", "framing", "compression", "inputKind", "verifiedArtifact")
        CatalogBulkImportSource(ResourceUri(text(v["contentUri"])), text(v["displayName"]), ProviderId(text(v["provider"])),
            LanguageTag(text(v["language"])), readKnowledge(v["sourceDate"], ::readDate),
            readKnowledge(v["sourceUri"]) { ResourceUri(text(it)) }, readKnowledge(v["expectedChecksum"], ::readHash),
            readKnowledge(v["expectedBytes"], ::integer), readKnowledge(v["notice"], ::readNotice),
            enumValue(v["framing"]), enumValue(v["compression"]), enumValue(v["inputKind"]),
            optional(v["verifiedArtifact"]) { readArtifact(objectValue(it)) })
    }
    private fun catalogSourceJson(s: CatalogBulkImportSource) = obj(
        "contentUri" to str(s.contentUri.value), "displayName" to str(s.displayName), "provider" to str(s.provider.value),
        "language" to str(s.language.value), "sourceDate" to knowledge(s.sourceDate, ::dateJson),
        "sourceUri" to knowledge(s.sourceUri) { str(it.value) }, "expectedChecksum" to knowledge(s.expectedChecksum, ::hashJson),
        "expectedBytes" to knowledge(s.expectedBytes, ::num), "notice" to knowledge(s.notice, ::noticeJson),
        "framing" to str(s.framing.name), "compression" to str(s.compression.name), "inputKind" to str(s.inputKind.name),
        "verifiedArtifact" to nullable(s.verifiedArtifact, ::artifactJson))

    // Drop causes too: parser/constructor exceptions may contain untrusted source text.
    private inline fun <T> guarded(action: () -> T): T = try { action() }
        catch (_: Exception) { throw IllegalArgumentException("Invalid acquisition payload") }

    private fun encode(type: String, value: JsonObject): String {
        // Bound serialization before allocating an oversized output String.
        val output = StringBuilder()
        val writer = object : Writer() {
            override fun write(buffer: CharArray, offset: Int, count: Int) {
                require(count <= MAX_BYTES - output.length)
                output.append(buffer, offset, count)
            }
            override fun flush() = Unit
            override fun close() = Unit
        }
        Streams.write(obj("version" to num(VERSION), "type" to str(type), "value" to value), JsonWriter(writer))
        val payload = output.toString()
        envelope(payload, type) // Symmetric byte, Unicode and nesting validation.
        return payload
    }
    private fun envelope(payload: String, type: String): JsonObject {
        require(payload.length <= MAX_BYTES && payload.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = ScryfallCodec.parse(payload, MAX_NESTING).fields("version", "type", "value")
        require(integer(root["version"]) == VERSION.toLong() && text(root["type"]) == type)
        return objectValue(root["value"])
    }
    private fun planJson(p: DownloadPlan) = obj(
        "jobId" to str(p.jobId.value), "request" to requestJson(p.request),
        "planHash" to hashJson(p.planHash), "createdAt" to dateJson(p.createdAt))
    private fun readPlan(v: JsonObject): DownloadPlan {
        v.fields("jobId", "request", "planHash", "createdAt")
        return DownloadPlan(EntityId(text(v["jobId"])), readRequest(objectValue(v["request"])),
            readHash(v["planHash"]), readDate(v["createdAt"]))
    }
    private fun requestJson(r: AcquisitionRequest) = obj(
        "intent" to intentJson(r.intent), "displayName" to str(r.displayName), "source" to sourceJson(r.source),
        "languageScope" to str(r.languageScope.name), "sourceVersion" to knowledge(r.sourceVersion, ::str),
        "sourceDate" to knowledge(r.sourceDate, ::dateJson), "notice" to knowledge(r.notice, ::noticeJson),
        "limits" to limitsJson(r.limits), "wifiOnly" to JsonPrimitive(r.wifiOnly))
    private fun readRequest(v: JsonObject): AcquisitionRequest {
        v.fields("intent", "displayName", "source", "languageScope", "sourceVersion", "sourceDate", "notice", "limits", "wifiOnly")
        return AcquisitionRequest(readIntent(objectValue(v["intent"])), text(v["displayName"]), readSource(objectValue(v["source"])),
            enumValue(v["languageScope"]), readKnowledge(v["sourceVersion"], ::text), readKnowledge(v["sourceDate"], ::readDate),
            readKnowledge(v["notice"], ::readNotice), readLimits(objectValue(v["limits"])), bool(v["wifiOnly"]))
    }
    private fun intentJson(i: DownloadIntent) = obj(
        "id" to str(i.id.value), "kind" to str(i.kind.name), "source" to str(i.source.value), "resource" to str(i.resource.value),
        "version" to str(i.version), "language" to nullable(i.language) { str(it.value) },
        "packId" to nullable(i.packId) { str(it.value) }, "checksum" to nullable(i.checksum, ::hashJson),
        "expectedBytes" to knowledge(i.expectedBytes, ::num))
    private fun readIntent(v: JsonObject): DownloadIntent {
        v.fields("id", "kind", "source", "resource", "version", "language", "packId", "checksum", "expectedBytes")
        return DownloadIntent(EntityId(text(v["id"])), enumValue(v["kind"]), ProviderId(text(v["source"])), ResourceUri(text(v["resource"])),
            text(v["version"]), optional(v["language"]) { LanguageTag(text(it)) }, optional(v["packId"]) { EntityId(text(it)) },
            optional(v["checksum"], ::readHash), readKnowledge(v["expectedBytes"], ::integer))
    }
    private fun sourceJson(s: AcquisitionSource): JsonObject = when (s) {
        is AcquisitionSource.RemoteFile -> obj("type" to str("remoteFile"), "uri" to str(s.uri.value),
            "allowedRedirectOrigins" to array(s.allowedRedirectOrigins) { obj("scheme" to str(it.scheme), "host" to str(it.host), "port" to num(it.port)) },
            "framing" to str(s.framing.name), "compression" to str(s.compression.name),
            "expectedBytes" to knowledge(s.expectedBytes, ::num), "expectedSha256" to knowledge(s.expectedSha256, ::hashJson))
        is AcquisitionSource.EnglishSearch -> obj("type" to str("englishSearch"), "firstPage" to str(s.firstPage.value), "queryPolicyVersion" to str(s.queryPolicyVersion))
    }
    private fun readSource(v: JsonObject): AcquisitionSource = when (text(v["type"])) {
        "remoteFile" -> {
            v.fields("type", "uri", "allowedRedirectOrigins", "framing", "compression", "expectedBytes", "expectedSha256")
            AcquisitionSource.RemoteFile(ResourceUri(text(v["uri"])), SnapshotList.copyOf(list(v["allowedRedirectOrigins"]).map {
                val o = objectValue(it).fields("scheme", "host", "port")
                Origin(text(o["scheme"]), text(o["host"]), int(o["port"]))
            }), enumValue(v["framing"]), enumValue(v["compression"]), readKnowledge(v["expectedBytes"], ::integer), readKnowledge(v["expectedSha256"], ::readHash))
        }
        "englishSearch" -> {
            v.fields("type", "firstPage", "queryPolicyVersion")
            AcquisitionSource.EnglishSearch(ResourceUri(text(v["firstPage"])), text(v["queryPolicyVersion"]))
        }
        else -> error("Invalid source")
    }
    private fun limitsJson(l: AcquisitionLimits) = obj(
        "profileVersion" to str(l.profileVersion), "maxTransferBytes" to num(l.maxTransferBytes), "maxArtifactBytes" to num(l.maxArtifactBytes),
        "maxDecodedBytes" to num(l.maxDecodedBytes), "maxRecords" to num(l.maxRecords), "maxPages" to num(l.maxPages),
        "maxPageBytes" to num(l.maxPageBytes), "maxRecordBytes" to num(l.maxRecordBytes), "maxChunkBytes" to num(l.maxChunkBytes), "maxNesting" to num(l.maxNesting))
    private fun readLimits(v: JsonObject): AcquisitionLimits {
        v.fields("profileVersion", "maxTransferBytes", "maxArtifactBytes", "maxDecodedBytes", "maxRecords", "maxPages", "maxPageBytes", "maxRecordBytes", "maxChunkBytes", "maxNesting")
        return AcquisitionLimits(text(v["profileVersion"]), integer(v["maxTransferBytes"]), integer(v["maxArtifactBytes"]), integer(v["maxDecodedBytes"]),
            integer(v["maxRecords"]), integer(v["maxPages"]), int(v["maxPageBytes"]), int(v["maxRecordBytes"]), int(v["maxChunkBytes"]), int(v["maxNesting"]))
    }
    private fun artifactJson(a: VerifiedArtifact) = obj(
        "jobId" to str(a.jobId.value), "framing" to str(a.framing.name), "compression" to str(a.compression.name), "bytes" to num(a.bytes),
        "sha256" to hashJson(a.sha256), "upstreamChecksumMatched" to JsonPrimitive(a.upstreamChecksumMatched),
        "recordCount" to knowledge(a.recordCount, ::num), "pageCount" to num(a.pageCount), "provenance" to provenanceJson(a.provenance))
    private fun readArtifact(v: JsonObject): VerifiedArtifact {
        v.fields("jobId", "framing", "compression", "bytes", "sha256", "upstreamChecksumMatched", "recordCount", "pageCount", "provenance")
        return VerifiedArtifact(EntityId(text(v["jobId"])), enumValue(v["framing"]), enumValue(v["compression"]), integer(v["bytes"]),
            readHash(v["sha256"]), bool(v["upstreamChecksumMatched"]), readKnowledge(v["recordCount"], ::integer), integer(v["pageCount"]), readProvenance(objectValue(v["provenance"])))
    }
    private fun provenanceJson(p: AcquisitionProvenance) = obj(
        "provider" to str(p.provider.value), "requestedUri" to str(p.requestedUri.value), "finalUri" to str(p.finalUri.value),
        "sourceVersion" to knowledge(p.sourceVersion, ::str), "sourceDate" to knowledge(p.sourceDate, ::dateJson),
        "retrievalStartedAt" to dateJson(p.retrievalStartedAt), "retrievalCompletedAt" to dateJson(p.retrievalCompletedAt),
        "etag" to knowledge(p.etag, ::str), "lastModified" to knowledge(p.lastModified, ::str),
        "consistency" to str(p.consistency.name), "languageScope" to str(p.languageScope.name), "notice" to knowledge(p.notice, ::noticeJson))
    private fun readProvenance(v: JsonObject): AcquisitionProvenance {
        v.fields("provider", "requestedUri", "finalUri", "sourceVersion", "sourceDate", "retrievalStartedAt", "retrievalCompletedAt", "etag", "lastModified", "consistency", "languageScope", "notice")
        return AcquisitionProvenance(ProviderId(text(v["provider"])), ResourceUri(text(v["requestedUri"])), ResourceUri(text(v["finalUri"])),
            readKnowledge(v["sourceVersion"], ::text), readKnowledge(v["sourceDate"], ::readDate), readDate(v["retrievalStartedAt"]), readDate(v["retrievalCompletedAt"]),
            readKnowledge(v["etag"], ::text), readKnowledge(v["lastModified"], ::text), enumValue(v["consistency"]), enumValue(v["languageScope"]), readKnowledge(v["notice"], ::readNotice))
    }
    private fun noticeJson(n: SourceNotice) = obj("provider" to str(n.provider.value), "title" to str(n.title), "license" to knowledge(n.license, ::str),
        "attribution" to str(n.attribution), "uri" to nullable(n.uri) { str(it.value) })
    private fun readNotice(e: JsonElement): SourceNotice {
        val v = objectValue(e).fields("provider", "title", "license", "attribution", "uri")
        return SourceNotice(ProviderId(text(v["provider"])), text(v["title"]), readKnowledge(v["license"], ::text), text(v["attribution"]), optional(v["uri"]) { ResourceUri(text(it)) })
    }
    private fun hashJson(h: ContentHash) = obj("algorithm" to str(h.algorithm), "hex" to str(h.hex))
    private fun readHash(e: JsonElement): ContentHash {
        val v = objectValue(e).fields("algorithm", "hex")
        return ContentHash(text(v["algorithm"]), text(v["hex"]))
    }
    private fun dateJson(t: UtcTimestamp) = obj("seconds" to num(t.epochSeconds), "nanos" to num(t.nanoAdjustment))
    private fun readDate(e: JsonElement): UtcTimestamp {
        val v = objectValue(e).fields("seconds", "nanos")
        return UtcTimestamp(integer(v["seconds"]), int(v["nanos"]))
    }
    private fun evidenceJson(e: Evidence) = obj("description" to str(e.description), "source" to nullable(e.source) { str(it.value) }, "recordedAt" to nullable(e.recordedAt, ::dateJson))
    private fun readEvidence(e: JsonElement): Evidence {
        val v = objectValue(e).fields("description", "source", "recordedAt")
        return Evidence(text(v["description"]), optional(v["source"]) { ResourceUri(text(it)) }, optional(v["recordedAt"], ::readDate))
    }
    private fun <T> knowledge(k: Knowledge<T>, encode: (T) -> JsonElement): JsonObject = when (k) {
        is Knowledge.Known -> obj("state" to str("known"), "value" to encode(k.value))
        is Knowledge.Absent -> obj("state" to str("absent"), "reason" to str(k.reason))
        is Knowledge.Unknown -> obj("state" to str("unknown"), "reason" to str(k.reason), "evidence" to array(k.evidence, ::evidenceJson))
    }
    private fun <T> readKnowledge(e: JsonElement, decode: (JsonElement) -> T): Knowledge<T> {
        val v = objectValue(e)
        return when (text(v["state"])) {
            "known" -> { v.fields("state", "value"); Knowledge.Known(decode(v["value"])) }
            "absent" -> { v.fields("state", "reason"); Knowledge.Absent(text(v["reason"])) }
            "unknown" -> { v.fields("state", "reason", "evidence"); Knowledge.Unknown(text(v["reason"]), SnapshotList.copyOf(list(v["evidence"]).map(::readEvidence))) }
            else -> error("Invalid knowledge state")
        }
    }
    private fun obj(vararg fields: Pair<String, JsonElement>) = JsonObject().apply { fields.forEach { (key, value) -> add(key, value) } }
    private fun str(s: String): JsonElement { require(s.length <= MAX_BYTES && s.toByteArray(Charsets.UTF_8).size <= MAX_BYTES); return JsonPrimitive(s) }
    private fun num(n: Number): JsonElement = JsonPrimitive(n)
    private fun <T> array(values: List<T>, encode: (T) -> JsonElement): JsonArray {
        require(values.size <= MAX_BYTES / 2)
        return JsonArray().apply { values.forEach { add(encode(it)) } }
    }
    private fun <T> nullable(value: T?, encode: (T) -> JsonElement): JsonElement = value?.let(encode) ?: JsonNull.INSTANCE
    private fun <T> optional(e: JsonElement, decode: (JsonElement) -> T): T? = if (e.isJsonNull) null else decode(e)
    private fun JsonObject.fields(vararg names: String): JsonObject { require(keySet() == names.toSet()); return this }
    private fun objectValue(e: JsonElement): JsonObject { require(e.isJsonObject); return e.asJsonObject }
    private fun list(e: JsonElement): JsonArray { require(e.isJsonArray); return e.asJsonArray }
    private fun text(e: JsonElement): String { require(e.isJsonPrimitive && e.asJsonPrimitive.isString); return e.asString }
    private fun bool(e: JsonElement): Boolean { require(e.isJsonPrimitive && e.asJsonPrimitive.isBoolean); return e.asBoolean }
    private fun integer(e: JsonElement): Long { require(e.isJsonPrimitive && e.asJsonPrimitive.isNumber); return e.asBigDecimal.longValueExact() }
    private fun int(e: JsonElement): Int { val n = integer(e); require(n in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()); return n.toInt() }
    private inline fun <reified E : Enum<E>> enumValue(e: JsonElement): E = enumValueOf(text(e))
}
