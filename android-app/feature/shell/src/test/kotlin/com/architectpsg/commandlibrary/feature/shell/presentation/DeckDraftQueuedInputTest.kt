package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.*
import org.junit.Test

/** Pure live-owner field merging only; session ownership and repository durability are separate. */
class DeckDraftQueuedInputTest {
    private val cancel = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private val entry = DeckEntry(EntityId("entry"), CardIdentity(Knowledge.Absent("oracle"),Knowledge.Absent("printing"),null,
        Knowledge.Known("Card"),LanguageTag("en")),2,BoardRole.MAIN,Knowledge.Absent("finish"),"saved notes",SnapshotList.empty(),SnapshotList.empty())
    private fun result(entries: SnapshotList<DeckEntry> = SnapshotList.of(entry)) = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE,"deck"),"Saved",
        Knowledge.Absent("format"),"saved description",Knowledge.Absent("folder"),SnapshotList.empty(),UtcTimestamp(1),UtcTimestamp(2),Revision(1),
        entries,Knowledge.Absent("budget"),null,SnapshotList.empty(),SnapshotList.empty(),"saved primer")
    private fun merge(captured: DeckDraftBody, residual: DeckDraftBody?, queued: DeckDraftBody?, deck: DeckSnapshot=result(), added: EntityId?=null) =
        DeckDraftQueuedInput.afterOwnCommit(captured,residual,queued,deck,added,cancel)
    private fun <T> ok(value: Outcome<T>): T { assertTrue(value.toString(),value is Outcome.Success); return (value as Outcome.Success).value }
    private fun failure(value: Outcome<*>, code: FailureCode) { assertTrue(value.toString(),value is Outcome.Failure); assertEquals(code,(value as Outcome.Failure).error.code) }
    private fun originalEntry() = DeckDraftBody.Entry(Revision(0),entry.id,"02","old notes",BoardRole.SIDEBOARD,
        Knowledge.Unknown("unresolved",SnapshotList.empty()),SnapshotList.of(EntryLabel(EntityId("category"),"Staged")))

    @Test fun newerInvalidTextSurvivesWhileUnchangedConsumedFieldsUseResult() {
        val before=DeckDraftBody.Details(Revision(0),"Saved","old description","old primer")
        val queued=before.copy(name="\uD800",primer="typed after submission")
        assertEquals(DeckDraftBody.Details(Revision(1),"\uD800","saved description","typed after submission"),ok(merge(before,null,queued)))
        assertEquals("Saved",before.name); assertEquals(Revision(0),queued.baseRevision)
    }
    @Test fun invalidResidualIsRetainedWithoutQueuedTyping() {
        val before=DeckDraftBody.Details(Revision(0),"","old description","old primer")
        val residual=DeckDraftBody.Details(Revision(1),"","saved description","saved primer")
        assertEquals(residual,ok(merge(before,residual,null)))
    }
    @Test fun changedFieldCanResolveAnOlderInvalidResidual() {
        val before=DeckDraftBody.Details(Revision(0),"","old description","old primer")
        val residual=DeckDraftBody.Details(Revision(1),"","saved description","saved primer")
        assertEquals(DeckDraftBody.Details(Revision(1),"Next name","saved description","saved primer"),ok(merge(before,residual,before.copy(name="Next name"))))
    }
    @Test fun unchangedCanonicalFieldsNeedNoAdditionalRawDelta() {
        val before=DeckDraftBody.Details(Revision(0),"Saved","old description","old primer")
        assertNull(ok(merge(before,null,null)))
    }
    @Test fun entryAliasUsesCanonicalQuantityAndRetainsStagedChoicesAndNewPartialNumber() {
        val before=originalEntry()
        val residual=before.copy(baseRevision=Revision(1),quantity="2",notes="saved notes")
        val expected=residual.copy(quantity="-",notes="later\uDFFF")
        assertEquals(expected,ok(merge(before,residual,before.copy(quantity="-",notes="later\uDFFF"))))
        assertEquals(residual,ok(merge(before,residual,null)))
    }
    @Test fun explicitEntryChoicesConsumeUnchangedFieldsButKeepLaterText() {
        val before=originalEntry()
        val after=entry.copy(board=before.board,finish=before.finish,labels=before.labels)
        assertEquals(DeckDraftBody.Entry(Revision(1),entry.id,"3","saved notes",after.board,after.finish,after.labels),
            ok(merge(before,null,before.copy(quantity="3"),result(SnapshotList.of(after)))))
    }
    @Test fun successfulAddTransitionsQueuedTextToExactlyOneFrozenEntryIdentity() {
        val selection=DeckDraftSelection(entry.identity,GenerationId("evidence"))
        val before=DeckDraftBody.Add(Revision(0),"2","old notes",entry.board,entry.finish,entry.labels,selection)
        val queued=before.copy(quantity="-",notes="new typing")
        assertEquals(DeckDraftBody.Entry(Revision(1),entry.id,"-","new typing",entry.board,entry.finish,entry.labels),
            ok(merge(before,null,queued,added=entry.id)))
        assertNull(ok(merge(before,null,null,added=entry.id)))
    }
    @Test fun addCannotRebaseAChangedSelectorOrInventItsCreatedIdentity() {
        val before=DeckDraftBody.Add(Revision(0),"2","old",entry.board,entry.finish,entry.labels,null)
        failure(merge(before,null,before.copy(selection=DeckDraftSelection(entry.identity,null)),added=entry.id),FailureCode.STALE_REVISION)
        failure(merge(before,null,before,added=EntityId("missing")),FailureCode.CORRUPT_DATA)
    }
    @Test fun anotherBaseModeOrTargetCannotBeRebased() {
        val before=originalEntry()
        failure(merge(before,null,before.copy(baseRevision=Revision(1))),FailureCode.STALE_REVISION)
        failure(merge(before,null,before.copy(entryId=EntityId("other"))),FailureCode.STALE_REVISION)
        failure(merge(before,null,DeckDraftBody.Details(Revision(0),"a","b","c")),FailureCode.STALE_REVISION)
    }
    @Test fun malformedResidualOrMissingResultEntryCannotBeAccepted() {
        val before=originalEntry()
        failure(merge(before,before,null),FailureCode.CORRUPT_DATA)
        failure(merge(before,DeckDraftBody.Details(Revision(1),"a","b","c"),null),FailureCode.CORRUPT_DATA)
        failure(merge(before,null,null,result(SnapshotList.empty())),FailureCode.CORRUPT_DATA)
    }
    @Test fun removeHasNoQueuedEditorContinuation() {
        val before=DeckDraftBody.Remove(Revision(0),entry.id)
        assertNull(ok(merge(before,null,before,result(SnapshotList.empty()))))
        failure(merge(before,null,before),FailureCode.CORRUPT_DATA)
    }
    @Test fun cancellationPropagatesBeforeAnyMerge() {
        val before=originalEntry()
        val stopped=object:CancelToken { override val isCancelled=true; override fun throwIfCancelled() { throw CancellationException("stop") } }
        try { DeckDraftQueuedInput.afterOwnCommit(before,null,before,result(),null,stopped);fail("Expected cancellation") }
        catch (_:CancellationException) { }
    }
}
