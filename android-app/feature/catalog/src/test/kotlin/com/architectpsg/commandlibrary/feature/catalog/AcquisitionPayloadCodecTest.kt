package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

class AcquisitionPayloadCodecTest {
    private val hash = ContentHash("sHa-256", "aB".repeat(32))
    private val time = UtcTimestamp(-123, 987654321)
    private val evidence = SnapshotList.of(Evidence("Synthetic evidence", ResourceUri("https://example.invalid/evidence"), time), Evidence("No URI or date", null, null))
    private val unknown = Knowledge.Unknown("Not supplied", evidence)
    private val notice = SourceNotice(ProviderId("synthetic"), "", Knowledge.Known(""), "Attribution", null)
    private val limits = AcquisitionLimits("fixture-v1", Long.MAX_VALUE, 1000, 2000, 30, 20, 256, 128, 64, 32)
    private fun plan(search: Boolean = false): DownloadPlan {
        val uri = ResourceUri("https://example.invalid/source")
        val source = if (search) AcquisitionSource.EnglishSearch(uri, "policy-v1") else AcquisitionSource.RemoteFile(uri,
            SnapshotList.of(Origin("https", "example.invalid", 443), Origin("http", "example.invalid", 80)), ArtifactFraming.JSON_ARRAY,
            ArtifactCompression.GZIP, Knowledge.Known(123L), Knowledge.Known(hash))
        return DownloadPlan(EntityId("job"), AcquisitionRequest(DownloadIntent(EntityId("intent"), DownloadKind.CATALOG, ProviderId("synthetic"), uri,
            "local-v1", LanguageTag.ENGLISH, EntityId("pack"), hash, Knowledge.Absent("Unknown size")), "Synthetic plan", source,
            AcquisitionLanguageScope.ENGLISH_CARDS, unknown, unknown, Knowledge.Known(notice), limits, true), hash, time)
    }
    private fun artifact() = VerifiedArtifact(EntityId("job"), ArtifactFraming.JSONL, ArtifactCompression.NONE, Long.MAX_VALUE, hash, false,
        Knowledge.Known(0L), Long.MAX_VALUE, AcquisitionProvenance(ProviderId("synthetic"), ResourceUri("https://example.invalid/request"),
            ResourceUri("https://example.invalid/final"), unknown, unknown, time, UtcTimestamp(456, 123456789),
            Knowledge.Known(""), Knowledge.Absent("No header"), AcquisitionConsistency.NON_ATOMIC_TRAVERSAL,
            AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED, Knowledge.Known(notice)))
    private fun mutatePlan(action: (JsonObject) -> Unit): String = JsonParser.parseString(AcquisitionPayloadCodec.encodePlan(plan())).asJsonObject.apply(action).toString()
    private fun mutateArtifact(action: (JsonObject) -> Unit): String = JsonParser.parseString(AcquisitionPayloadCodec.encodeArtifact(artifact())).asJsonObject.apply(action).toString()
    private fun value(o: JsonObject) = o.getAsJsonObject("value")
    private fun request(o: JsonObject) = value(o).getAsJsonObject("request")
    private fun rejectPlan(payload: String) = rejected { AcquisitionPayloadCodec.decodePlan(payload) }
    private fun rejectArtifact(payload: String) = rejected { AcquisitionPayloadCodec.decodeArtifact(payload) }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (e: IllegalArgumentException) {
            assertEquals("Invalid acquisition payload", e.message); assertNull(e.cause)
        }
    }

    @Test fun bothSourcesRoundTripEveryNestedField() {
        for (search in listOf(false, true)) {
            val original = plan(search)
            assertEquals(original, AcquisitionPayloadCodec.decodePlan(AcquisitionPayloadCodec.encodePlan(original)))
        }
    }
    @Test fun artifactRoundTripPreservesIntervalAndUnknownMetadata() {
        val original = artifact()
        assertEquals(original, AcquisitionPayloadCodec.decodeArtifact(AcquisitionPayloadCodec.encodeArtifact(original)))
    }
    @Test fun allKnowledgeStatesIncludingKnownEmptyAndEmptyEvidenceRoundTrip() {
        val strings: List<Knowledge<String>> = listOf(Knowledge.Known(""), Knowledge.Absent("Absent"), unknown, Knowledge.Unknown("Unknown", SnapshotList.empty()))
        val dates: List<Knowledge<UtcTimestamp>> = listOf(Knowledge.Known(time), Knowledge.Absent("Absent"), unknown)
        val notices: List<Knowledge<SourceNotice>> = listOf(Knowledge.Known(notice), Knowledge.Absent("Absent"), unknown)
        for (s in strings) for (d in dates) for (n in notices) {
            val p = plan().let { it.copy(request = it.request.copy(sourceVersion = s, sourceDate = d, notice = n)) }
            assertEquals(p, AcquisitionPayloadCodec.decodePlan(AcquisitionPayloadCodec.encodePlan(p)))
            val a = artifact().let { it.copy(provenance = it.provenance.copy(sourceVersion = s, sourceDate = d, notice = n, etag = s, lastModified = s)) }
            assertEquals(a, AcquisitionPayloadCodec.decodeArtifact(AcquisitionPayloadCodec.encodeArtifact(a)))
        }
        for (k in listOf<Knowledge<Long>>(Knowledge.Known(0L), Knowledge.Absent("Absent"), unknown)) {
            val a = artifact().copy(recordCount = k)
            assertEquals(a, AcquisitionPayloadCodec.decodeArtifact(AcquisitionPayloadCodec.encodeArtifact(a)))
        }
    }
    @Test fun nullableIntentFieldsAndRemoteKnowledgeRoundTrip() {
        for (license in listOf<Knowledge<String>>(Knowledge.Known(""), Knowledge.Absent("Absent"), unknown)) {
            val n = notice.copy(license = license, uri = ResourceUri("https://example.invalid/notice"))
            val base = plan()
            val p = base.copy(request = base.request.copy(notice = Knowledge.Known(n)))
            assertEquals(p, AcquisitionPayloadCodec.decodePlan(AcquisitionPayloadCodec.encodePlan(p)))
        }
        for (k in listOf<Knowledge<Long>>(Knowledge.Known(1L), Knowledge.Absent("Absent"), unknown)) {
            val base = plan()
            val remote = base.request.source as AcquisitionSource.RemoteFile
            val p = base.copy(request = base.request.copy(intent = base.request.intent.copy(language = null, packId = null, checksum = null, expectedBytes = k),
                source = remote.copy(allowedRedirectOrigins = SnapshotList.empty(), expectedBytes = k, expectedSha256 = unknown)))
            assertEquals(p, AcquisitionPayloadCodec.decodePlan(AcquisitionPayloadCodec.encodePlan(p)))
        }
    }
    @Test fun unsupportedVersionsAndEnvelopeTypesReject() {
        for (bad in listOf<JsonElement>(JsonPrimitive(0), JsonPrimitive(2), JsonPrimitive("1"), JsonPrimitive(true), JsonNull.INSTANCE, JsonPrimitive(1.5))) {
            rejectPlan(mutatePlan { it.add("version", bad) }); rejectArtifact(mutateArtifact { it.add("version", bad) })
        }
        rejectPlan(AcquisitionPayloadCodec.encodeArtifact(artifact()))
        rejectArtifact(AcquisitionPayloadCodec.encodePlan(plan()))
        rejectPlan(mutatePlan { it.addProperty("type", "future") })
    }
    @Test fun primitivesCannotBeCoerced() {
        for (bad in listOf<JsonElement>(JsonPrimitive(12), JsonPrimitive(true), JsonNull.INSTANCE, JsonObject(), JsonArray())) {
            rejectPlan(mutatePlan { value(it).add("jobId", bad) })
            rejectArtifact(mutateArtifact { value(it).add("jobId", bad) })
        }
        for (bad in listOf<JsonElement>(JsonPrimitive("true"), JsonPrimitive(1), JsonNull.INSTANCE)) {
            rejectPlan(mutatePlan { request(it).add("wifiOnly", bad) })
            rejectArtifact(mutateArtifact { value(it).add("upstreamChecksumMatched", bad) })
        }
    }
    @Test fun fractionalOverflowAndWrongNumericTypesRejectWithoutRounding() {
        for (raw in listOf("1.5", "9223372036854775808", "-9223372036854775809", "1e99999", "\"1\"", "true", "null")) {
            val n = JsonParser.parseString(raw)
            rejectPlan(mutatePlan { value(it).getAsJsonObject("createdAt").add("seconds", n) })
            rejectArtifact(mutateArtifact { value(it).add("bytes", n) })
        }
        rejectPlan(mutatePlan { request(it).getAsJsonObject("limits").addProperty("maxPageBytes", 2147483648L) })
        rejectPlan(mutatePlan { value(it).getAsJsonObject("createdAt").addProperty("nanos", 2147483648L) })
    }
    @Test fun enumSourceAndKnowledgeVariantsReject() {
        for (field in listOf("framing", "compression")) rejectArtifact(mutateArtifact { value(it).addProperty(field, "future") })
        rejectArtifact(mutateArtifact { value(it).getAsJsonObject("provenance").addProperty("consistency", "future") })
        rejectPlan(mutatePlan { request(it).addProperty("languageScope", "future") })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("intent").addProperty("kind", "future") })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("source").addProperty("type", "future") })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("sourceVersion").addProperty("state", "future") })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("sourceVersion").addProperty("value", "unwanted") })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("sourceVersion").remove("evidence") })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("sourceVersion").addProperty("reason", " ") })
    }
    @Test fun missingExtraDuplicateFieldsAndMalformedJsonReject() {
        rejectPlan(mutatePlan { it.remove("version") })
        rejectPlan(mutatePlan { request(it).addProperty("path", "/private/synthetic") })
        rejectPlan("{\"secret-source\":1,\"secret-source\":2}")
        rejectPlan(AcquisitionPayloadCodec.encodePlan(plan()).replace("\"jobId\":\"job\"", "\"jobId\":\"job\",\"jobId\":\"job\""))
        for (raw in listOf("[]", "null", "{", "{} {}", "{\"version\":NaN}", "{\"x\":\"\\uD800\"}")) rejectPlan(raw)
    }
    @Test fun constructorInvariantsRemainEnforced() {
        rejectPlan(mutatePlan { value(it).addProperty("jobId", " ") })
        rejectPlan(mutatePlan { request(it).addProperty("displayName", "x".repeat(513)) })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("limits").addProperty("maxTransferBytes", 0) })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("limits").addProperty("maxChunkBytes", 65537) })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("limits").addProperty("maxNesting", 129) })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("limits").addProperty("maxRecordBytes", 257) })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("source").getAsJsonObject("expectedBytes").addProperty("value", -1) })
        rejectPlan(mutatePlan { request(it).getAsJsonObject("source").getAsJsonArray("allowedRedirectOrigins")[0].asJsonObject.addProperty("port", 65536) })
        rejectPlan(mutatePlan { value(it).getAsJsonObject("createdAt").addProperty("nanos", 1000000000) })
        rejectArtifact(mutateArtifact { value(it).addProperty("bytes", -1) })
        rejectArtifact(mutateArtifact { value(it).addProperty("pageCount", -1) })
        rejectArtifact(mutateArtifact { value(it).getAsJsonObject("recordCount").addProperty("value", -1) })
    }
    @Test fun hashMetadataUsesCoreSha256Convention() {
        for (algorithm in listOf("sha256", "SHA-512", "")) {
            rejectPlan(mutatePlan { value(it).getAsJsonObject("planHash").addProperty("algorithm", algorithm) })
            rejectArtifact(mutateArtifact { value(it).getAsJsonObject("sha256").addProperty("algorithm", algorithm) })
        }
        for (hex in listOf("a".repeat(63), "a".repeat(65), "z".repeat(64), "")) {
            rejectPlan(mutatePlan { value(it).getAsJsonObject("planHash").addProperty("hex", hex) })
            rejectArtifact(mutateArtifact { value(it).getAsJsonObject("sha256").addProperty("hex", hex) })
        }
        rejectPlan(mutatePlan { request(it).getAsJsonObject("source").getAsJsonObject("expectedSha256").getAsJsonObject("value").addProperty("algorithm", "sha256") })
    }
    @Test fun exactUtf8ByteBoundaryIsAcceptedAndOneMoreByteRejects() {
        val base = plan().let { it.copy(request = it.request.copy(sourceVersion = Knowledge.Known(""))) }
        val overhead = AcquisitionPayloadCodec.encodePlan(base).toByteArray(Charsets.UTF_8).size
        val padding = "x".repeat(AcquisitionPayloadCodec.MAX_BYTES - overhead)
        val exact = base.copy(request = base.request.copy(sourceVersion = Knowledge.Known(padding)))
        val payload = AcquisitionPayloadCodec.encodePlan(exact)
        assertEquals(AcquisitionPayloadCodec.MAX_BYTES, payload.toByteArray(Charsets.UTF_8).size)
        assertEquals(exact, AcquisitionPayloadCodec.decodePlan(payload))
        rejected { AcquisitionPayloadCodec.encodePlan(base.copy(request = base.request.copy(sourceVersion = Knowledge.Known(padding + "x")))) }
        rejectPlan(payload + " ")
    }
    @Test fun byteAndNestingBoundsApplyOnBothDirections() {
        rejectPlan(" ".repeat(AcquisitionPayloadCodec.MAX_BYTES + 1))
        rejectArtifact(" ".repeat(AcquisitionPayloadCodec.MAX_BYTES + 1))
        val large = plan().let { it.copy(request = it.request.copy(sourceVersion = Knowledge.Known("é".repeat(AcquisitionPayloadCodec.MAX_BYTES / 2)))) }
        rejected { AcquisitionPayloadCodec.encodePlan(large) }
        val largeArtifact = artifact().let { it.copy(provenance = it.provenance.copy(etag = Knowledge.Known("x".repeat(AcquisitionPayloadCodec.MAX_BYTES)))) }
        rejected { AcquisitionPayloadCodec.encodeArtifact(largeArtifact) }
        val nested = "{\"x\":".repeat(34) + "0" + "}".repeat(34)
        rejectPlan(nested); rejectArtifact(nested)
    }
}
