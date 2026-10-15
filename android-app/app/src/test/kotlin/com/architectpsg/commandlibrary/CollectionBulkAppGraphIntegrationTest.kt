package com.architectpsg.commandlibrary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.collection.codec.LocalInventoryPatchCodec
import com.architectpsg.commandlibrary.data.storage.collection.codec.OwnedLotPayloadCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.di.SystemUtcClock
import com.architectpsg.commandlibrary.feature.collection.persistence.SqlLocalInventory
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Actual activity/UI/AppGraph/Room journey on host. No catalog proof, native or process-kill claim. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class CollectionBulkAppGraphIntegrationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val cancel = object : CancelToken { override val isCancelled=false; override fun throwIfCancelled()=Unit }
    private fun <T> ok(value:Outcome<T>):T { assertTrue(value.toString(),value is Outcome.Success);return (value as Outcome.Success).value }
    @Before fun hostBudget() = budget(300)
    @After fun resetBudget() = budget(60)
    private fun budget(seconds:Long) {
        Class.forName("androidx.test.espresso.IdlingPolicies").getMethod("setMasterPolicyTimeout",Long::class.javaPrimitiveType,TimeUnit::class.java)
            .invoke(null,seconds,TimeUnit.SECONDS)
    }
    private fun inventoryClick(tag:String) {
        rule.onNodeWithTag("collection-inventory").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performClick()
    }
    private fun waitDisplayedRevision(revision:Revision) {
        val label=rule.activity.getString(ShellR.string.collection_revision,revision.value)
        rule.onNodeWithTag("collection-inventory").performScrollToIndex(0)
        rule.waitUntil(20_000) { rule.waitForIdle();rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun dialogScroll(tag:String) { rule.onNodeWithTag("collection-bulk-dialog-list").performScrollToNode(hasTestTag(tag)) }

    @Test fun actualBulkUiZeroAndExplicitLocationClearPersistUndoAndReopenWithoutChangingUnselectedLot() {
        rule.waitUntil(20_000) { rule.waitForIdle();rule.onAllNodesWithTag("shell-nav-collection").fetchSemanticsNodes().isNotEmpty() }
        val app=rule.activity.application as CommandLibraryApplication
        val ports=runBlocking { ok(app.graph.ports()) };val inventory=checkNotNull(ports.localInventory)
        val suffix=UUID.randomUUID().toString()
        fun lot(letter:String)=OwnedLot(EntityId("actual-bulk-$letter-$suffix"),CardIdentity(Knowledge.Unknown("No Oracle",SnapshotList.empty()),
            Knowledge.Absent("No acquired printing"),ProviderIdentity(ProviderId("synthetic-raw"),"foreign","$letter-$suffix"),
            Knowledge.Known("Synthetic bulk $letter"),LanguageTag.ENGLISH),Knowledge.Unknown("No finish",SnapshotList.empty()),
            CardCondition.UNKNOWN,"Shelf $letter",3,"Untouched note $letter",Revision(0),Knowledge.Unknown("No acquisition date",SnapshotList.empty()))
        val created=listOf(lot("a"),lot("b"),lot("c"))
        val before=runBlocking {
            val current=ok(inventory.snapshot(cancel))
            ok(inventory.apply(CollectionPatch(SnapshotList.copyOf(created.map{CollectionEdit.Add(it)})),current.revision,EntityId("seed-$suffix"),cancel))
            ok(inventory.snapshot(cancel))
        }
        rule.onNodeWithTag("shell-nav-collection").performClick()
        waitDisplayedRevision(before.revision)
        inventoryClick("collection-bulk-select")
        for(lot in created.take(2)) inventoryClick("collection-bulk-lot-${lot.id.value}")
        inventoryClick("collection-bulk-edit")
        val setQuantity="bulk-set-${ShellR.string.collection_quantity}"
        dialogScroll(setQuantity);rule.onNodeWithTag(setQuantity).performClick()
        dialogScroll("collection-bulk-quantity");rule.onNodeWithTag("collection-bulk-quantity").performTextReplacement("0")
        val setLocation="bulk-set-${ShellR.string.collection_location}"
        dialogScroll(setLocation);rule.onNodeWithTag(setLocation).performClick()
        dialogScroll("collection-bulk-location");rule.onNodeWithTag("collection-bulk-location").performTextReplacement("")
        rule.onNodeWithTag("collection-bulk-preview").performClick()
        rule.waitUntil(20_000) {
            rule.waitForIdle();rule.onAllNodesWithTag("collection-bulk-confirm").fetchSemanticsNodes().any{!it.config.contains(SemanticsProperties.Disabled)}
        }
        assertEquals(before,runBlocking{ok(inventory.snapshot(cancel))})
        rule.onNodeWithTag("collection-bulk-confirm").performClick()
        rule.waitUntil(20_000) { rule.waitForIdle();rule.onAllNodesWithTag("collection-bulk-confirm").fetchSemanticsNodes().isEmpty() }
        val saved=runBlocking{ok(inventory.snapshot(cancel))}
        assertEquals(before.revision.value+1,saved.revision.value)
        val selected=created.take(2).map{it.id}.toSet()
        assertEquals(before.lots.map{if(it.id in selected)it.copy(quantity=0,location="",revision=saved.revision)else it},saved.lots.toList())
        waitDisplayedRevision(saved.revision)
        inventoryClick("collection-undo")
        rule.waitUntil(20_000) { rule.waitForIdle();runBlocking{ok(inventory.snapshot(cancel)).revision.value==saved.revision.value+1} }
        val undone=runBlocking{ok(inventory.snapshot(cancel))}
        assertEquals(before.lots.map{if(it.id in selected)it.copy(revision=undone.revision)else it},undone.lots.toList())
        runBlocking {
            ok(StorageDatabase.open(app,AppGraph.DATABASE_FILE)).use{db->
                val reader=SqlLocalInventory(db,OwnedLotPayloadCodec(),LocalInventoryPatchCodec(),SystemUtcClock,ok(db.capabilities(cancel)).maxBoundParameters)
                assertEquals(undone,ok(reader.snapshot(cancel)))
            }
            assertTrue(ok(ports.settings.read(cancel)).consents.isEmpty())
        }
    }
}
