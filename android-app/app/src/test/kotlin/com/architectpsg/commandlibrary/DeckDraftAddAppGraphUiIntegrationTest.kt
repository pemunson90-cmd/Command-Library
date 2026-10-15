package com.architectpsg.commandlibrary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config

/** Synthetic local catalog seeded before real activity startup; no online/provider completeness claim. */
@RunWith(AndroidJUnit4::class)
@Config(shadows=[RealHostDirectoryLinux::class,RealHostBlockGuardOs::class])
class DeckDraftAddAppGraphUiIntegrationTest {
    private val rule=createAndroidComposeRule<MainActivity>()
    private val suffix=UUID.randomUUID().toString()
    private val cardName="Synthetic draft $suffix"
    private val oracle=OracleId(ProviderId("scryfall"),"oracle-$suffix")
    private val cancel=object:CancelToken {override val isCancelled=false;override fun throwIfCancelled()=Unit}
    private fun <T> ok(value:Outcome<T>):T {assertTrue(value.toString(),value is Outcome.Success);return(value as Outcome.Success).value}
    private val seed=TestRule { base,_ -> object:Statement() {
        override fun evaluate() {
            val app=ApplicationProvider.getApplicationContext<CommandLibraryApplication>()
            runBlocking {
                val ports=ok(app.graph.ports())
                val raw="""[{"object":"card","id":"printing-$suffix","oracle_id":"${oracle.raw}","lang":"en","name":"$cardName","set":"tst","collector_number":"1","keywords":[],"scryfall_uri":"https://scryfall.com/card/tst/1/synthetic","oracle_text":"Draw a card.","layout":"normal"}]""".toByteArray(Charsets.UTF_8)
                val source=CatalogImportSource(ResourceUri("content://selected/draft-$suffix"),"Synthetic",ProviderId("scryfall"),LanguageTag.ENGLISH,
                    Knowledge.Unknown("Date not supplied",SnapshotList.empty()),Knowledge.Absent("Source not supplied"),
                    Knowledge.Absent("Hash not supplied"),Knowledge.Known(raw.size.toLong()),Knowledge.Absent("Notice not supplied"))
                ok(ports.catalogImport.`import`(source,flowOf(ByteChunk(0,0,SnapshotBytes(raw),null,true)),{},cancel))
            }
            base.evaluate()
        }
    } }
    @get:Rule val chain:RuleChain=RuleChain.outerRule(seed).around(rule)
    @Before fun hostBudget()=budget(300)
    @After fun resetBudget()=budget(60)
    private fun budget(seconds:Long) {
        Class.forName("androidx.test.espresso.IdlingPolicies")
            .getMethod("setMasterPolicyTimeout",Long::class.javaPrimitiveType,TimeUnit::class.java)
            .invoke(null,seconds,TimeUnit.SECONDS)
    }
    private fun await(check:()->Boolean)=rule.waitUntil(20_000) {rule.waitForIdle();check()}
    private fun enabled(tag:String)=await {rule.onAllNodesWithTag(tag).fetchSemanticsNodes().any {!it.config.contains(SemanticsProperties.Disabled)}}
    private fun overviewClick(tag:String) {
        enabled(tag)
        rule.onNodeWithTag("deck-library-overview").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performClick()
    }
    @Test fun actualPickerStagesQualifiedRawSelectionThenExplicitlyAddsExactlyOnceAndUndoes() {
        await {rule.onAllNodesWithTag("shell-nav-decks").fetchSemanticsNodes().isNotEmpty()}
        val ports=runBlocking {ok((rule.activity.application as CommandLibraryApplication).graph.ports())}
        val library=checkNotNull(ports.localDecks);val drafts=checkNotNull(ports.localDeckDrafts)
        val name="Picker $suffix"
        rule.onNodeWithTag("shell-nav-decks").performClick()
        rule.onNodeWithTag("deck-library-new").performClick()
        rule.onNodeWithTag("deck-library-name").performTextReplacement(name)
        rule.onNodeWithTag("deck-library-confirm").performClick()
        enabled("deck-add-entry")
        val id=runBlocking {ok(library.list(PageRequest(20,null),cancel)).items.single {it.name==name}.id}
        val before=runBlocking {ok(library.load(id,cancel))}
        overviewClick("deck-add-entry")
        enabled("deck-editor-search")
        rule.onNodeWithTag("deck-editor-search").performTextReplacement(cardName)
        rule.onNodeWithTag("deck-editor-search-submit").performScrollTo().performClick()
        enabled("deck-add-result-${oracle.raw}")
        rule.onNodeWithTag("deck-add-result-${oracle.raw}").performScrollTo().performClick()
        rule.onNodeWithTag("deck-editor-quantity").performScrollTo().performTextReplacement("2")
        await {runBlocking {(ok(drafts.load(id,cancel)).draft?.body as? DeckDraftBody.Add)?.let {it.quantity=="2" && it.selection?.identity?.oracleId==Knowledge.Known(oracle)}==true}}
        val raw=runBlocking {ok(drafts.load(id,cancel)).draft!!.body as DeckDraftBody.Add}
        val selected=checkNotNull(raw.selection)
        assertEquals(Knowledge.Absent("No printing selected"),selected.identity.printingId)
        assertNull(selected.identity.raw)
        assertEquals(LanguageTag.ENGLISH,selected.identity.language)
        assertEquals(Knowledge.Known(cardName),selected.identity.originalName)
        assertNotNull(selected.generation)
        assertEquals(before,runBlocking {ok(library.load(id,cancel))})
        enabled("deck-editor-save")
        rule.onNodeWithTag("deck-editor-save").performClick()
        await {runBlocking {ok(library.load(id,cancel)).entries.size==1 && ok(drafts.load(id,cancel)).draft==null}}
        val added=runBlocking {ok(library.load(id,cancel))}
        assertEquals(before.revision.value+1,added.revision.value)
        assertEquals(selected.identity,added.entries.single().identity)
        assertEquals(2,added.entries.single().quantity)
        rule.onNodeWithText(rule.activity.getString(ShellR.string.deck_draft_close)).performClick()
        await {rule.onAllNodesWithTag("deck-editor-quantity").fetchSemanticsNodes().isEmpty()}
        overviewClick("deck-undo")
        await {runBlocking {ok(library.load(id,cancel)).revision.value==added.revision.value+1}}
        val undone=runBlocking {ok(library.load(id,cancel))}
        assertEquals(before.copy(revision=undone.revision,modifiedAt=undone.modifiedAt),undone)
        assertTrue(runBlocking {ok(ports.settings.read(cancel)).consents.isEmpty()})
    }
}
