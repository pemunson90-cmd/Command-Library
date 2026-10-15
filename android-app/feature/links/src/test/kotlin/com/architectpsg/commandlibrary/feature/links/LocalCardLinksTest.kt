package com.architectpsg.commandlibrary.feature.links

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic source-qualified fixtures; these make no live vendor or transport claims. */
class LocalCardLinksTest {
    private val links = LocalCardLinks()
    private val provider = ProviderId("synthetic-source")
    private val provenance = Provenance(provider, ResourceUri("https://source.test/snapshot"), Knowledge.Known("fixture-v1"),
        Knowledge.Absent("No source date"), UtcTimestamp(1), Knowledge.Absent("No effective date"), GenerationId("fixture"), null,
        SourceNotice(provider, "Synthetic source", Knowledge.Absent("Test data"), "Fixture attribution", null))
    private val card = CardRecord(OracleId(provider, "oracle/raw"), "Éowyn // Ice & \"Snow\"", absent(), absent(), absent(), absent(), absent(), absent(),
        CardStats(absent(), absent(), absent(), absent()), absent(), absent(), absent(), "fixture", LanguageTag.ENGLISH,
        SnapshotList.empty(), SnapshotList.empty(), provenance, Revision(0))
    private val printing = PrintingRecord(CardId(provider, "print/raw"), Knowledge.Known(card.oracleId), "set", absent(), "001a/★", LanguageTag("fr"),
        absent(), absent(), absent(), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), SnapshotList.empty(), provenance)

    private fun absent(): Knowledge.Absent = Knowledge.Absent("Not supplied in fixture")
    private fun target(id: String = "scryfall", host: String = "example.test", port: Int = 443, enabled: Boolean = true) =
        LinkDestination(EntityId(id), "Fixture $id", SnapshotList.of(Origin("https", host, port)), enabled)
    private fun configured(id: String = "scryfall", pattern: String = "https://example.test/?q={name}", tokens: List<String> = listOf("name")) =
        LinkTemplate(EntityId(id), pattern, SnapshotList.copyOf(tokens))
    private fun preferences(targets: List<LinkDestination> = listOf(target()), templates: List<LinkTemplate> = emptyList(), prefer: Boolean = true, fallback: Boolean = true) =
        LinkPreferences(SnapshotList.copyOf(targets), SnapshotList.copyOf(templates), prefer, fallback)
    private fun supplied(key: String = "scryfall_uri", url: String = "https://example.test/card?ref=source&utm_source=fixture%2f+raw") =
        SuppliedLink(key, ResourceUri(url), provenance)
    private fun withLinks(vararg supplied: SuppliedLink) = printing.copy(suppliedLinks = SnapshotList.of(*supplied))
    private fun success(outcome: Outcome<SnapshotList<ResolvedLink>>): SnapshotList<ResolvedLink> {
        assertTrue(outcome.toString(), outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }
    private fun invalid(outcome: Outcome<*>, code: FailureCode = FailureCode.INVALID_INPUT) {
        assertTrue(outcome.toString(), outcome is Outcome.Failure)
        assertEquals(code, (outcome as Outcome.Failure).error.code)
    }

    @Test fun suppliedWinsAndPreservesBytesAttributionAndSourceQualifiedPrinting() {
        val actual = supplied()
        val link = success(links.resolve(card, withLinks(actual), preferences(templates = listOf(configured())))).single()
        assertEquals(actual.uri, link.uri)
        assertEquals(actual.provenance, link.provenance)
        assertEquals(printing.id, link.selectedPrinting)
        assertEquals(LinkResolutionKind.SUPPLIED, link.kind)
        assertTrue(link.warning is Knowledge.Known)
    }

    @Test fun explicitTemplatePrecedenceIsControlledByPreference() {
        val configured = configured(pattern = "https://example.test/{printing_id}?q={name}", tokens = listOf("printing_id", "name"))
        val link = success(links.resolve(card, withLinks(supplied()), preferences(templates = listOf(configured), prefer = false))).single()
        assertEquals(LinkResolutionKind.TEMPLATE, link.kind)
        assertEquals("https://example.test/print%2Fraw?q=%C3%89owyn%20%2F%2F%20Ice%20%26%20%22Snow%22", link.uri.value)
        assertNull(link.selectedPrinting)
        assertNull(link.provenance)
        val warning = (link.warning as Knowledge.Known).value
        assertTrue(warning.contains("user-configured"))
        assertTrue(warning.contains("not an exact"))
    }

    @Test fun suppliedIsUsedWithoutTemplateEvenWhenPrintingPreferenceIsFalse() {
        assertEquals(LinkResolutionKind.SUPPLIED, success(links.resolve(card, withLinks(supplied()), preferences(prefer = false))).single().kind)
    }

    @Test fun explicitTemplateWorksWithAndWithoutSelectedPrinting() {
        for (selected in listOf(null, printing)) {
            val result = success(links.resolve(card, selected, preferences(templates = listOf(configured())))).single()
            assertEquals(LinkResolutionKind.TEMPLATE, result.kind)
            assertNull(result.selectedPrinting)
        }
        val pattern = "https://example.test/{oracle_id}/{printing_id}/{set}/{collector_number}/{lang}"
        val template = configured(pattern = pattern, tokens = listOf("oracle_id", "printing_id", "set", "collector_number", "lang"))
        assertEquals("https://example.test/oracle%2Fraw/print%2Fraw/set/001a%2F%E2%98%85/fr",
            success(links.resolve(card, printing, preferences(templates = listOf(template)))).single().uri.value)
        val missing = links.resolve(card, null, preferences(templates = listOf(template))) as Outcome.Unavailable
        assertTrue(missing.detail.contains("scryfall"))
        assertTrue(missing.detail.contains("printing_id"))
    }

    @Test fun missingEnabledTargetMakesBatchAtomicallyUnavailableInOrder() {
        val preferences = preferences(targets = listOf(target("scryfall"), target("cardkingdom")))
        val result = links.resolve(card, withLinks(supplied()), preferences) as Outcome.Unavailable
        assertTrue(result.detail.contains("cardkingdom"))
        assertTrue(result.detail.contains("missing"))
    }

    @Test fun successfulBatchFollowsDestinationPreferenceOrderAndSkipsDisabled() {
        val ordered = listOf(target("cardkingdom"), target("spellbook", enabled = false), target("scryfall"))
        val result = success(links.resolve(card, withLinks(supplied()), preferences(ordered, listOf(configured("cardkingdom")))))
        assertEquals(listOf("cardkingdom", "scryfall"), result.map { it.destination.id.value })
        assertEquals(listOf(LinkResolutionKind.TEMPLATE, LinkResolutionKind.SUPPLIED), result.map { it.kind })
    }

    @Test fun noEnabledTargetsProduceEmptySuccess() {
        assertTrue(success(links.resolve(card, null, preferences(targets = emptyList()))).isEmpty())
        assertTrue(success(links.resolve(card, printing, preferences(targets = listOf(target(enabled = false))))).isEmpty())
    }

    @Test fun exactSupportedSuppliedKeysAreRecognized() {
        val keys = mapOf("scryfall" to "scryfall_uri", "gatherer" to "related_uris.gatherer", "edhrec" to "related_uris.edhrec",
            "tcgplayer" to "purchase_uris.tcgplayer", "cardmarket" to "purchase_uris.cardmarket", "cardhoarder" to "purchase_uris.cardhoarder")
        for ((id, key) in keys) {
            val actual = supplied(key)
            val result = success(links.resolve(card, withLinks(actual), preferences(listOf(target(id))))).single()
            assertEquals(id, LinkResolutionKind.SUPPLIED, result.kind)
            assertEquals(actual.uri, result.uri)
        }
    }

    @Test fun apiInfiniteDeckArticleAndInventedVendorKeysAreNeverPurchaseRoutes() {
        for ((id, key) in listOf("scryfall" to "uri", "tcgplayer" to "related_uris.tcgplayer_infinite_articles",
            "tcgplayer" to "related_uris.tcgplayer_infinite_decks", "cardkingdom" to "purchase_uris.cardkingdom", "spellbook" to "related_uris.spellbook")) {
            assertTrue("$id $key", links.resolve(card, withLinks(supplied(key)), preferences(listOf(target(id)))) is Outcome.Unavailable)
        }
    }

    @Test fun searchPermissionAndRealVendorIdsDoNotInventRoutes() {
        val selected = printing.copy(vendorIds = SnapshotList.of(ProviderIdentity(ProviderId("tcgplayer"), "product", "12345")))
        for (fallback in listOf(true, false)) for (id in listOf("scryfall", "gatherer", "edhrec", "spellbook", "tcgplayer", "cardmarket", "cardkingdom", "cardhoarder")) {
            assertTrue(id, links.resolve(card, selected, preferences(listOf(target(id)), fallback = fallback)) is Outcome.Unavailable)
        }
    }

    @Test fun knownOracleMismatchFailsBeforeSuppliedOrTemplates() {
        for (oracle in listOf(OracleId(provider, "other"), OracleId(ProviderId("other-source"), card.oracleId.raw))) {
            val selected = withLinks(supplied()).copy(oracleId = Knowledge.Known(oracle))
            for (prefer in listOf(true, false)) invalid(links.resolve(card, selected, preferences(templates = listOf(configured()), prefer = prefer)), FailureCode.SOURCE_MISMATCH)
        }
    }

    @Test fun unknownAbsentAndReversibleRelationsStayUnknownWithoutFaceNameMatching() {
        val evidence = SnapshotList.of(Evidence("Synthetic unresolved whole-card relation", null, null))
        val unknown = Knowledge.Unknown("Reversible face oracles do not prove a whole-card identity", evidence)
        for (relation in listOf(unknown, absent())) {
            val selected = withLinks(supplied()).copy(oracleId = relation,
                relationships = SnapshotList.of(FaceRelationship(FaceRelationshipKind.OTHER, Knowledge.Known(card.oracleId), absent(), absent(), absent(), evidence)))
            for (prefer in listOf(true, false)) {
                val result = links.resolve(card, selected, preferences(templates = listOf(configured()), prefer = prefer))
                assertTrue(result.toString(), result is Outcome.Unknown)
                if (relation === unknown) assertEquals(evidence, (result as Outcome.Unknown).evidence)
            }
        }
    }

    @Test fun duplicateTargetsTemplatesAndOrphansAreInvalid() {
        invalid(links.resolve(card, printing, preferences(listOf(target(), target()))))
        invalid(links.resolve(card, printing, preferences(templates = listOf(configured(), configured()))))
        invalid(links.resolve(card, printing, preferences(templates = listOf(configured("orphan")))))
        invalid(links.resolve(card, null, preferences(listOf(target(enabled = false), target(enabled = false)))))
    }

    @Test fun conflictingSourceDuplicatesFailAndIdenticalRepeatedSourceIsUnambiguous() {
        invalid(links.resolve(card, withLinks(supplied(), supplied(url = "https://example.test/other")), preferences()))
        invalid(links.resolve(card, withLinks(supplied(), supplied().copy(provenance = provenance.copy(fetchedAt = UtcTimestamp(2)))), preferences()))
        assertEquals(1, success(links.resolve(card, withLinks(supplied(), supplied()), preferences())).size)
    }

    @Test fun perTemplateAllowancesAreDuplicateFreeSupportedAndComplete() {
        for (tokens in listOf(listOf("name", "name"), listOf("vendor_id"), emptyList(), listOf("NAME"), listOf("name", "scryfall_uri"))) {
            invalid(links.resolve(card, printing, preferences(templates = listOf(configured(tokens = tokens)))))
        }
        assertEquals(1, success(links.resolve(card, printing, preferences(templates = listOf(configured(tokens = listOf("name", "set")))))).size)
        invalid(links.resolve(card, withLinks(supplied()), preferences(templates = listOf(configured(pattern = "https://{name}/x")))))
    }

    @Test fun explicitOriginsRequireExactSchemeHostAndEffectivePort() {
        for (url in listOf("http://example.test/a", "https://evil.test/a", "https://example.test.evil.test/a", "https://sub.example.test/a",
            "https://example.test:444/a", "https://example.test./a", "https://example.test@evil.test/a", "https://example.test%2eevil.test/a")) {
            invalid(links.resolve(card, withLinks(supplied(url = url)), preferences()))
        }
        for (url in listOf("https://example.test/a", "HTTPS://EXAMPLE.TEST:443/a", "https://example.test:00443/a")) {
            assertEquals(url, success(links.resolve(card, withLinks(supplied(url = url)), preferences())).single().uri.value)
        }
        assertEquals(1, success(links.resolve(card, withLinks(supplied(url = "https://example.test:8443/a")), preferences(listOf(target(port = 8443))))).size)
    }

    @Test fun originConfigurationRejectsWildcardsUnicodeUppercaseAndEmbeddedAuthority() {
        invalid(links.resolve(card, printing, preferences(listOf(target().copy(allowedOrigins = SnapshotList.empty())))))
        for (host in listOf("*.test", "EXAMPLE.TEST", "éxample.test", "example.test/evil", "user@example.test", "example.test:443", ".test")) {
            invalid(links.resolve(card, printing, preferences(listOf(target(host = host)), listOf(configured()))))
        }
    }

    @Test fun templatesAlsoRequireAllowedOriginAndCanUseExplicitCustomTarget() {
        invalid(links.resolve(card, printing, preferences(templates = listOf(configured(pattern = "https://example.test.evil.test/{name}")))))
        val custom = target("custom-query")
        assertEquals("custom-query", success(links.resolve(card, null, preferences(listOf(custom), listOf(configured("custom-query"))))).single().destination.id.value)
    }

    @Test fun malformedSuppliedSourceFailsEvenWhenTemplateIsPreferred() {
        for (url in listOf("javascript:alert(1)", "//example.test/a", "https://example.test:0/a", "https://example.test:/a", "https://user@example.test/a",
            "https://example.test/%GG", "https://example.test/a\\b", "https://example.test/a\nb")) {
            for (prefer in listOf(true, false)) invalid(links.resolve(card, withLinks(supplied(url = url)), preferences(templates = listOf(configured()), prefer = prefer)))
        }
    }

    @Test fun cardmarketKeepsSourceUrlAndExplicitInexactWarning() {
        val actual = supplied("purchase_uris.cardmarket")
        val result = success(links.resolve(card, withLinks(actual), preferences(listOf(target("cardmarket"))))).single()
        assertEquals(actual.uri, result.uri)
        assertEquals(actual.provenance, result.provenance)
        assertTrue((result.warning as Knowledge.Known).value.contains("may be inexact"))
    }

    @Test fun unsafeTargetIdIsNotEchoedInUnavailableDetail() {
        val result = links.resolve(card, null, preferences(listOf(target("evil\n\"target")))) as Outcome.Unavailable
        assertTrue(result.detail.contains("[configured target]"))
        assertFalse(result.detail.contains('\n'))
    }

    @Test fun configurationLimitsPermit128AndReject129() {
        val targets = (1..128).map { target("custom$it") }
        val templates = targets.map { configured(it.id.value) }
        assertEquals(128, success(links.resolve(card, null, preferences(targets, templates))).size)
        invalid(links.resolve(card, null, preferences(targets + target("custom129"), templates)))
        invalid(links.resolve(card, null, preferences(targets, templates + configured("custom129"))))
    }

    @Test fun suppliedUrlUtf8AndTemplateConfigurationSizeLimitsAreEnforced() {
        val prefix = "https://example.test/"
        val boundary = prefix + "x".repeat(16384 - prefix.length)
        assertEquals(boundary, success(links.resolve(card, withLinks(supplied(url = boundary)), preferences())).single().uri.value)
        invalid(links.resolve(card, withLinks(supplied(url = boundary + "x")), preferences()))
        invalid(links.resolve(card, withLinks(supplied(url = prefix + "雪".repeat(6000))), preferences()))
        invalid(links.resolve(card, withLinks(supplied()), preferences(templates = listOf(configured(pattern = boundary + "x", tokens = emptyList())))))
    }
}
