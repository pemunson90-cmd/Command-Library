package com.architectpsg.commandlibrary.feature.catalog

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.common.DecimalComparisonKeys
import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

/** All records/provenance are explicitly synthetic; conversion alone grants no installed authority. */
class CatalogSearchProjectorTest {
    private val provider = ProviderId("synthetic-provider")
    private val generation = GenerationId("synthetic-projection")
    private val unknown = Knowledge.Unknown("Synthetic fixture", SnapshotList.empty())
    private val provenance = Provenance(provider, null, unknown, unknown, UtcTimestamp(123), unknown, generation, null, null)
    private fun raw(change: JsonObject.() -> Unit = {}): String = JsonParser.parseString(bulkRecord("p", "o")).asJsonObject.apply(change).toString()
    private fun project(raw: String = raw()) = CatalogSearchProjector.project(raw, provenance, BULK_NO_CANCEL)
    private fun CatalogSearchProjection.rows(table: CatalogProjectionTable) = rows.filter { it.table == table }.map { it.data }
    private fun RowData.cell(name: String): SqlValue = columns.single { it.name == name }.value
    private fun RowData.text(name: String): String = (cell(name) as SqlValue.Text).value
    private fun RowData.number(name: String): Long = (cell(name) as SqlValue.Integer).value
    private fun key(value: String) = requireNotNull(DecimalComparisonKeys.sourceKey(value))

    @Test fun actualIdsNullableEvidenceAndTextOrdinalsStayIndependentOfAuthority() {
        val input = raw { addProperty("name", "  Ａé — Æther  "); addProperty("oracle_text", ""); add("colors", JsonParser.parseString("[]")); add("keywords", JsonParser.parseString("[\"First strike\",\"Flying\",\"Flying\"]")) }
        val result = project(input)
        assertEquals(provider, result.provider); assertEquals(generation, result.generation)
        assertEquals(OracleId(provider,"o"),result.oracleId); assertEquals(CardId(provider,"p"),result.printingId)
        val card = result.rows(CatalogProjectionTable.CARD).single()
        assertEquals(SqlValue.Null,card.cell("mana_value_key")); assertEquals(SqlValue.Null,card.cell("reserved"))
        assertEquals(0L,card.number("colors_mask")); assertEquals(SqlValue.Null,card.cell("identity_mask"))
        assertEquals(1L,card.number("oracle_text_known")); assertEquals(1L,card.number("keywords_known"))
        val texts=result.rows(CatalogProjectionTable.TEXT)
        assertEquals("aé — æther",texts.single { it.text("field")=="NAME" }.text("normalized_value"))
        assertEquals("",texts.single { it.text("field")=="ORACLE_TEXT" }.text("normalized_value"))
        assertEquals(listOf(0L,1L,2L),texts.filter { it.text("scope_kind")=="KEYWORD" }.map { it.number("scope_index") })
        assertTrue(result.rows.all { it.data.text("generation_id")==generation.value && it.data.text("provider")==provider.value })
    }

    @Test fun knownAndSymbolicNumericStatsUseExactCoreKeysWithoutZeroDefaults() {
        val result=project(raw { addProperty("cmc", java.math.BigDecimal("0.125")); addProperty("power","*"); addProperty("toughness","-0"); addProperty("loyalty","1."+"0".repeat(63)+"1"); addProperty("defense","1e2"); addProperty("reserved",false) })
        val card=result.rows(CatalogProjectionTable.CARD).single()
        assertEquals(key(".125"),card.text("mana_value_key")); assertEquals(SqlValue.Null,card.cell("power_key"))
        assertEquals(key("0"),card.text("toughness_key")); assertEquals(key("1."+"0".repeat(63)+"1"),card.text("loyalty_key"))
        assertEquals(SqlValue.Null,card.cell("defense_key")); assertEquals(0L,card.number("reserved"))
    }

    @Test fun actualFaceOrderFieldsAndUnknownColorsNeverInheritCardWideValues() {
        val input=raw { addProperty("layout","transform"); add("colors",JsonParser.parseString("[\"R\"]")); add("color_identity",JsonParser.parseString("[\"W\",\"U\",\"B\",\"R\",\"G\"]")); addProperty("cmc",7)
            add("card_faces",JsonParser.parseString("""[{"object":"card_face","name":"Front","oracle_text":"Draw.","power":"1","colors":[]},{"object":"card_face","name":"Back","type_line":"Creature — Test","toughness":"2","colors":["Z"]}]""")) }
        val result=project(input); val card=result.rows(CatalogProjectionTable.CARD).single(); val faces=result.rows(CatalogProjectionTable.FACE)
        assertEquals(8L,card.number("colors_mask")); assertEquals(31L,card.number("identity_mask")); assertEquals(2L,card.number("face_count"))
        assertEquals(listOf(0L,1L),faces.map { it.number("face_index") })
        assertEquals(0L,faces[0].number("colors_mask")); assertEquals(SqlValue.Null,faces[1].cell("colors_mask"))
        assertEquals(SqlValue.Null,faces[1].cell("power_key")); assertEquals(key("2"),faces[1].text("toughness_key"))
        assertTrue(faces.all { row -> row.columns.none { it.name in setOf("mana_value_key","identity_mask") } })
        val text=result.rows(CatalogProjectionTable.TEXT)
        assertEquals(listOf("draw."),text.filter { it.text("field")=="ORACLE_TEXT" }.map { it.text("normalized_value") })
        assertTrue(text.filter { it.text("field")=="ORACLE_TEXT" }.all { it.text("scope_kind")=="FACE" })
    }

    @Test fun printingOnlyReversibleRecordsDoNotInventOracleOrFaceFacts() {
        val result=project(bulkReversible("rev","first","second"))
        assertNull(result.oracleId); assertEquals(CardId(provider,"rev"),result.printingId)
        assertTrue(result.rows.all { it.table==CatalogProjectionTable.PRINTING })
        assertEquals(1,result.rows.size)
    }

    @Test fun individualLegalitiesKeepOriginalFormatIdentityAndOtherKnownEntries() {
        val result=project(raw { addProperty("game_changer",true); add("legalities",JsonParser.parseString("""{"Commander":"legal","odd:key":"banned","restricted":"restricted","not":"not_legal","future":"future-value","bad":17,"blank":null," ":"legal"}""")) })
        val print=result.rows(CatalogProjectionTable.PRINTING).single();assertEquals(1L,print.number("gamechanger"));assertEquals(1L,print.number("legalities_known"))
        val statuses=result.rows(CatalogProjectionTable.LEGALITY).associate { it.text("format") to it.text("status") }
        assertEquals(mapOf("Commander" to "LEGAL","odd:key" to "BANNED","restricted" to "RESTRICTED","not" to "NOT_LEGAL","future" to "UNKNOWN","bad" to "UNKNOWN","blank" to "UNKNOWN"),statuses)
        assertTrue(result.rows(CatalogProjectionTable.LEGALITY).all { it.text("provider")==provider.value })
    }

    @Test fun missingMalformedAndFalseFlagsRemainDistinctAndFinishesUseTokenBits() {
        for ((value, dictionaryKnown) in listOf("null" to 0L,"\"true\"" to 0L,"17" to 0L,"{}" to 1L)) {
            val result=project(raw { add("game_changer",JsonParser.parseString(value));add("legalities",JsonParser.parseString(value)) })
            val print=result.rows(CatalogProjectionTable.PRINTING).single()
            assertEquals(SqlValue.Null,print.cell("gamechanger"));assertEquals(dictionaryKnown,print.number("legalities_known"));assertTrue(result.rows(CatalogProjectionTable.LEGALITY).isEmpty())
        }
        val result=project(raw { addProperty("game_changer",false);add("finishes",JsonParser.parseString("[\"nonfoil\",\"etched\"]"));add("legalities",JsonParser.parseString("{}")) })
        val print=result.rows(CatalogProjectionTable.PRINTING).single()
        assertEquals(0L,print.number("gamechanger"));assertEquals(5L,print.number("finish_mask"));assertEquals(1L,print.number("legalities_known"))
    }

    @Test fun knownEmptyAndAbsentKeywordArraysDoNotCollapseIntoEachOther() {
        val empty=project(raw { add("keywords",JsonParser.parseString("[]")) })
        val missing=project(raw { remove("keywords") })
        assertEquals(1L,empty.rows(CatalogProjectionTable.CARD).single().number("keywords_known"))
        assertEquals(0L,missing.rows(CatalogProjectionTable.CARD).single().number("keywords_known"))
        assertTrue(empty.rows(CatalogProjectionTable.TEXT).none { it.text("field")=="KEYWORD" })
    }

    @Test fun strictDecoderRejectsInvalidLanguageDuplicateKeysAndMalformedUnicode() {
        val valid=raw()
        for (bad in listOf(valid.dropLast(1)+",\"id\":\"duplicate\"}",raw { addProperty("lang","fr") },raw { addProperty("name","\uD800") },raw { addProperty("oracle_id", "") },"[]"))
            assertThrows(IllegalArgumentException::class.java) { project(bad) }
    }

    @Test fun providedIdentitiesAreBoundedBeforeReplicationAndRequireGeneration() {
        for (bad in listOf(provenance.copy(generation=null),provenance.copy(generation=GenerationId("g".repeat(129))),provenance.copy(provider=ProviderId("é".repeat(65))),provenance.copy(provider=ProviderId("a\u0000b"))))
            assertThrows(IllegalArgumentException::class.java) { CatalogSearchProjector.project(raw(),bad,BULK_NO_CANCEL) }
    }

    @Test fun explicitFanoutBoundaryCountsBothTextFactsAndFtsMirrors() {
        val exact=project(raw { add("keywords",JsonParser.parseString(List(510) { "\"K\"" }.joinToString(",","[","]"))) })
        assertEquals(1024,exact.rows.size);assertEquals(2048,exact.estimatedStatements)
        assertThrows(SearchProjectionBudgetExceeded::class.java) { project(raw { add("keywords",JsonParser.parseString(List(511) { "\"K\"" }.joinToString(",","[","]"))) }) }
        assertThrows(SearchProjectionBudgetExceeded::class.java) { project(raw { add("legalities",JsonParser.parseString((1..1024).joinToString(",","{","}") { "\"f$it\":\"legal\"" })) }) }
    }

    @Test fun sourceAndNormalizedByteCeilingsFailExplicitlyWithoutPartialOutput() {
        assertThrows(SearchProjectionBudgetExceeded::class.java) { project(" ".repeat(8*1024*1024+1)) }
        assertThrows(SearchProjectionBudgetExceeded::class.java) { project("é".repeat(4*1024*1024+1)) }
        val unit=ScryfallCodec.normalize("\uFDFA").toByteArray(Charsets.UTF_8).size
        val count=(8*1024*1024)/(unit*2)+1
        assertThrows(SearchProjectionBudgetExceeded::class.java) { project(raw { addProperty("oracle_text","\uFDFA".repeat(count)) }) }
        val small=raw();assertNotNull(project(small+" ".repeat(8*1024*1024-small.length)))
    }

    @Test fun cancellationPropagatesInsideMaterializationAndSnapshotsAreImmutable() {
        var checks=0
        val cancel=object:CancelToken { override val isCancelled get()=checks>=12;override fun throwIfCancelled() { if(++checks>=12) throw CancellationException("Synthetic cancellation") } }
        assertThrows(CancellationException::class.java) { CatalogSearchProjector.project(raw(),provenance,cancel) }
        assertEquals(12,checks)
        val result=project();val before=result.rows.toList()
        assertThrows(RuntimeException::class.java) { @Suppress("UNCHECKED_CAST") (result.rows as MutableList<CatalogProjectionRow>).clear() }
        assertEquals(before,result.rows)
    }

    @Test fun generatedFactsFitActualExportedSchema9AndNeverPublishReadiness() {
        val result=project(raw { addProperty("oracle_text","Draw a card.");addProperty("game_changer",false);add("legalities",JsonParser.parseString("{\"commander\":\"legal\"}"))
            add("card_faces",JsonParser.parseString("""[{"object":"card_face","name":"Front","power":"1","colors":["U"]},{"object":"card_face","name":"Back","oracle_text":"Face text."}]""")) })
        BundledSQLiteDriver().open(":memory:").use { db ->
            fun exec(sql:String) { db.prepare(sql).use { it.step() } }
            exec("PRAGMA foreign_keys=ON")
            var folder=File(System.getProperty("user.dir"));var schema:File?=null
            while(schema==null) { val candidate=File(folder,"android-app/data/storage/schemas/com.architectpsg.commandlibrary.data.storage.schema.StorageSchemaDatabase/9.json");if(candidate.isFile)schema=candidate else folder=folder.parentFile?:error("Actual schema9 absent") }
            val root=JsonParser.parseString(schema.readText()).asJsonObject.getAsJsonObject("database")
            for(e in root.getAsJsonArray("entities")) { val v=e.asJsonObject;val table=v["tableName"].asString;exec(v["createSql"].asString.replace("\${TABLE_NAME}",table));for(i in v.getAsJsonArray("indices")?:emptyList())exec(i.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}",table)) }
            exec("INSERT INTO catalog_generation(generation_id,provider,format,content_uri,display_name,observed_at_epoch_seconds,observed_at_nano,content_sha256,observed_bytes,language_scope,payload_schema_version,parser_version,oracle_count,face_count,printing_count,status) VALUES('${generation.value}','${provider.value}','synthetic','content://synthetic/x','synthetic',1,0,'opaque',1,'en',1,'synthetic',1,2,1,'RETIRED')")
            exec("INSERT INTO oracle_card VALUES('${generation.value}','${provider.value}','o','en','Synthetic',NULL,'synthetic','synthetic','opaque','opaque')")
            for (index in 0..1) exec("INSERT INTO card_face VALUES('${generation.value}','${provider.value}','o','en',$index,NULL,'Synthetic face','opaque')")
            exec("INSERT INTO printing VALUES('${generation.value}','${provider.value}','p','${provider.value}','o','en','tst',NULL,'007a','en',NULL,NULL,NULL,'opaque')")
            exec("INSERT INTO catalog_search_build(generation_id,projection_version,status,phase,card_count,face_count,printing_count,text_count,legality_count) VALUES('${generation.value}',1,'PREPARING','CARD',0,0,0,0,0)")
            for(row in result.rows) {
                val cols=row.data.columns
                db.prepare("INSERT INTO `${row.table.sqlName}` (${cols.joinToString(",") { "`${it.name}`" }}) VALUES (${cols.joinToString(",") { "?" }})").use { s ->
                    cols.forEachIndexed { i,c -> when(val v=c.value) { is SqlValue.Text -> s.bindText(i+1,v.value);is SqlValue.Integer -> s.bindLong(i+1,v.value);SqlValue.Null -> s.bindNull(i+1);else -> error("Unexpected derived type") } };s.step()
                }
            }
            db.prepare("SELECT status FROM catalog_search_build").use { assertTrue(it.step());assertEquals("PREPARING",it.getText(0)) }
            db.prepare("SELECT count(*) FROM pragma_foreign_key_check").use { assertTrue(it.step());assertEquals(0L,it.getLong(0)) }
            assertEquals(result.rows.size*2,result.estimatedStatements)
            val actualBytes=result.rows.sumOf { row -> row.data.columns.sumOf { (it.value as? SqlValue.Text)?.value?.toByteArray(Charsets.UTF_8)?.size?.toLong()?:0L } }
            assertEquals(actualBytes,result.derivedStringBytes)
        }
    }
}
