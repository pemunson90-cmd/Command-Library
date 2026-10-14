package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.presentation.Problem
import com.architectpsg.commandlibrary.feature.shell.ui.problemText
import com.architectpsg.commandlibrary.feature.shell.ui.profiles.ProfileTags
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import android.os.Build
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Compose/controller UI over synthetic test-only profile data; persistence has separate probes. */
@RunWith(AndroidJUnit4::class)
class ProfilesUiTest {
    @get:Rule val rule=createComposeRule()
    private val profiles=UiProfilesFixture()
    private val shell=ShellUiHarness(rule,profiles)
    // A captured cold-start stack was actively instrumenting PhoneWindow after185s on this host.
    // Keep actual UI assertions; allow the SDK34 host sandbox to finish loading before idling fails.
    @Before fun allowHostSandboxColdStart() {
        hostIdleBudget(300)
    }
    @After fun close() {
        shell.close()
        hostIdleBudget(60)
    }
    private fun hostIdleBudget(seconds:Long) {
        if(Build.FINGERPRINT.contains("robolectric")) {
            // Compose supplies Espresso at runtime, without exposing it on this compile classpath.
            Class.forName("androidx.test.espresso.IdlingPolicies")
                .getMethod("setMasterPolicyTimeout",Long::class.javaPrimitiveType,TimeUnit::class.java)
                .invoke(null,seconds,TimeUnit.SECONDS)
        }
    }
    private fun open() { rule.onNodeWithTag("shell-nav-profiles").performClick();shell.waitForText(shell.string(R.string.profiles_title)) }
    private fun saved(name:String) {
        // Draft text is already in the editor: wait for actual accepted write/list before matching it.
        shell.awaitState {
            val state=shell.controllers.profiles!!.state.value
            state.editor==null && !state.busy && !state.loading
        }
        shell.waitForText(name)
    }
    private fun change(tag:String,value:String) { rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value) }
    private fun click(tag:String) { rule.onNodeWithTag(tag).performScrollTo().performClick() }

    @Test fun profileDestinationIsAbsentWhenNoCompletePortIsSupplied() {
        val absent=ShellUiHarness(rule)
        try { absent.show();rule.onNodeWithTag("shell-nav-profiles").assertDoesNotExist() } finally { absent.close() }
    }
    @Test fun createsAndEditsProfileThroughActualControllerThenConfirmsDeletion() {
        shell.show();open();shell.waitForText(shell.string(R.string.profiles_empty))
        click(ProfileTags.NEW);change(ProfileTags.NAME,"Ada");change(ProfileTags.LIFE,"-10");change(ProfileTags.STEP,"3");click(ProfileTags.SAVE)
        saved("Ada");val row=profiles.rows.values.single();assertEquals(-10,row.value.preferredLife);assertEquals(3,row.value.adjustmentStep)
        click("profile-edit-${row.id.value}");change(ProfileTags.NAME,"Ada edited");click(ProfileTags.SAVE)
        saved("Ada edited");assertEquals(Revision(1),profiles.rows[row.id]!!.revision)
        click("profile-delete-${row.id.value}");rule.onNodeWithTag(ProfileTags.CONFIRM_DELETE).assertIsDisplayed()
        rule.onNodeWithTag(ProfileTags.CANCEL_DELETE).performClick();assertEquals(1,profiles.rows.size)
        click("profile-delete-${row.id.value}");rule.onNodeWithTag(ProfileTags.CONFIRM_DELETE).performClick()
        shell.waitForText(shell.string(R.string.profiles_empty));assertTrue(profiles.rows.isEmpty())
    }
    @Test fun invalidFieldsAndFailedSaveKeepEnteredDraftAndShowFriendlyProblem() {
        shell.show();open();click(ProfileTags.NEW);change(ProfileTags.NAME,"Unsaved");change(ProfileTags.STEP,"0");click(ProfileTags.SAVE)
        shell.text(shell.string(R.string.profiles_invalid_step)).performScrollTo().assertIsDisplayed();assertTrue(profiles.rows.isEmpty())
        change(ProfileTags.STEP,"1");profiles.writeStatus=failure(FailureCode.PERSISTENCE);click(ProfileTags.SAVE)
        shell.waitForText(shell.string(problemText(Problem.PERSISTENCE)))
        rule.onNodeWithTag(ProfileTags.NAME).assertTextContains("Unsaved");assertTrue(profiles.rows.isEmpty())
        profiles.writeStatus=null;click(ProfileTags.SAVE);saved("Unsaved")
    }
    @Test fun savingShowsBusyStateAndPreventsDuplicateSubmissionAndCancel() {
        profiles.gate=CompletableDeferred();shell.show();open();click(ProfileTags.NEW);change(ProfileTags.NAME,"Pending");click(ProfileTags.SAVE)
        shell.awaitState { shell.controllers.profiles!!.state.value.busy }
        rule.onNodeWithTag(ProfileTags.SAVE).assertIsNotEnabled();rule.onNodeWithTag(ProfileTags.CANCEL).assertIsNotEnabled()
        profiles.gate!!.complete(Unit);saved("Pending");assertEquals(1,profiles.rows.size)
    }
    @Test fun navigatingAwayDuringSaveReturnsToActualCommittedResult() {
        profiles.gate=CompletableDeferred();shell.show();open();click(ProfileTags.NEW)
        change(ProfileTags.NAME,"Continued save");click(ProfileTags.SAVE)
        shell.awaitState { shell.controllers.profiles!!.state.value.busy }
        rule.onNodeWithTag("shell-nav-cards").performClick()
        rule.onNodeWithTag(ProfileTags.SAVE).assertDoesNotExist()
        assertTrue(shell.controllers.profiles!!.state.value.busy);assertTrue(profiles.rows.isEmpty())
        profiles.gate!!.complete(Unit)
        shell.awaitState { !shell.controllers.profiles!!.state.value.busy }
        open();shell.waitForText("Continued save");assertEquals(1,profiles.rows.size)
    }
    @Test fun busyDeleteDialogCanCloseWhileItsRealOperationContinues() {
        profiles.seed("ada","Ada");shell.show();open();shell.waitForText("Ada")
        click("profile-delete-ada");profiles.gate=CompletableDeferred()
        rule.onNodeWithTag(ProfileTags.CONFIRM_DELETE).performClick()
        shell.awaitState { shell.controllers.profiles!!.state.value.busy }
        rule.onNodeWithTag(ProfileTags.CANCEL_DELETE).assertIsEnabled().performClick()
        rule.onNodeWithTag(ProfileTags.CONFIRM_DELETE).assertDoesNotExist()
        assertTrue(profiles.rows.containsKey(EntityId("ada")))
        rule.onNodeWithTag("shell-nav-cards").performClick();profiles.gate!!.complete(Unit)
        shell.awaitState { !shell.controllers.profiles!!.state.value.busy }
        open();shell.waitForText(shell.string(R.string.profiles_empty));assertTrue(profiles.rows.isEmpty())
    }
    @Test fun staleEditKeepsDraftUntilExplicitConfirmedReload() {
        profiles.seed("ada","Ada");shell.show();open();shell.waitForText("Ada")
        click("profile-edit-ada");change(ProfileTags.NAME,"My draft")
        val old=profiles.rows[EntityId("ada")]!!
        profiles.rows[old.id]=old.copy(revision=Revision(1),value=old.value.copy(name="Other writer"))
        click(ProfileTags.SAVE);shell.waitForText(shell.string(problemText(Problem.STALE_REVISION)))
        rule.onNodeWithTag(ProfileTags.NAME).assertTextContains("My draft")
        click(ProfileTags.RELOAD);rule.onNodeWithTag(ProfileTags.CANCEL_RELOAD).performClick()
        rule.onNodeWithTag(ProfileTags.NAME).assertTextContains("My draft")
        click(ProfileTags.RELOAD);rule.onNodeWithTag(ProfileTags.CONFIRM_RELOAD).performClick()
        shell.waitForText("Other writer");assertEquals(Revision(1),shell.controllers.profiles!!.state.value.editor!!.original!!.revision)
    }
    @Test fun narrowLargeFontEditorScrollsToLabelledFieldsAndActions() {
        shell.show(width=360.dp,height=640.dp,fontScale=2f);open();click(ProfileTags.NEW)
        change(ProfileTags.NAME,"Large text");change(ProfileTags.LIFE,"40");change(ProfileTags.STEP,"5")
        rule.onNodeWithTag(ProfileTags.SAVE).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        saved("Large text");assertEquals(40,profiles.rows.values.single().value.preferredLife)
    }
    @Test fun wideProfileListUsesRealMoreCursorAndCanRetryFailedLoad() {
        profiles.pageSize=1
        profiles.seed("a","Ada");profiles.seed("b","Bea")
        profiles.listStatus=Outcome.Unavailable(UnavailableReason.OFFLINE,"Synthetic")
        shell.show(width=900.dp);open();shell.waitForText(shell.string(problemText(Problem.OFFLINE)))
        profiles.listStatus=null;click(ProfileTags.REFRESH);shell.waitForText("Ada")
        click(ProfileTags.MORE);shell.waitForText("Bea");assertNull(shell.controllers.profiles!!.state.value.nextCursor)
        rule.onNodeWithTag(ProfileTags.MORE).assertDoesNotExist()
    }
}

private class UiProfilesFixture:PlayerProfileRepository {
    val rows=ConcurrentHashMap<EntityId,PlayerProfileSnapshot>()
    @Volatile var writeStatus:Outcome<Nothing>?=null
    @Volatile var listStatus:Outcome<Nothing>?=null
    @Volatile var gate:CompletableDeferred<Unit>?=null
    var pageSize=20
    fun seed(id:String,name:String) {
        val life=CounterDefinition(EntityId("life"),"Life",null,null,20,CounterLossPolicy.AtOrBelow(0),false,CounterRole.LIFE)
        rows[EntityId(id)]=PlayerProfileSnapshot(EntityId(id),Revision(0),PlayerProfileDraft(name,20,SnapshotList.of(life),SeatAppearance(null,null),SnapshotList.empty()))
    }
    override suspend fun list(page:PageRequest,cancel:CancelToken):Outcome<Page<PlayerProfileSnapshot>> {
        cancel.throwIfCancelled();listStatus?.let{return it};val all=rows.values.sortedBy { it.value.name };val from=page.cursor?.toInt()?:0
        val values=all.drop(from).take(minOf(pageSize,page.limit));val next=(from+values.size).takeIf { it<all.size }?.toString()
        return Outcome.Success(Page(SnapshotList.copyOf(values),next,SnapshotReference(ProviderId("synthetic-ui-profiles"),null,null,null)))
    }
    override suspend fun read(id:EntityId,cancel:CancelToken):Outcome<PlayerProfileSnapshot> = rows[id]?.let { Outcome.Success(it) }?:Outcome.Unavailable(UnavailableReason.NOT_FOUND,"Synthetic")
    override suspend fun create(value:PlayerProfileDraft,cancel:CancelToken):Outcome<PlayerProfileSnapshot> {
        gate?.await();cancel.throwIfCancelled();writeStatus?.let{return it}
        val row=PlayerProfileSnapshot(EntityId("created-${rows.size}"),Revision(0),value);rows[row.id]=row;return Outcome.Success(row)
    }
    override suspend fun update(id:EntityId,value:PlayerProfileDraft,expected:Revision,cancel:CancelToken):Outcome<PlayerProfileSnapshot> {
        cancel.throwIfCancelled();writeStatus?.let{return it};if(rows[id]?.revision!=expected)return failure(FailureCode.STALE_REVISION)
        val row=PlayerProfileSnapshot(id,Revision(expected.value+1),value);rows[id]=row;return Outcome.Success(row)
    }
    override suspend fun delete(id:EntityId,expected:Revision,cancel:CancelToken):Outcome<Unit> {
        gate?.await();cancel.throwIfCancelled();writeStatus?.let{return it};if(rows[id]?.revision!=expected)return failure(FailureCode.STALE_REVISION)
        rows.remove(id);return Outcome.Success(Unit)
    }
}
