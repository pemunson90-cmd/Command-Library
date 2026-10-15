# Draft application integration packet — root implementation, architecture checkpoint

Existing campaign deck-ui/deck-persistence B02/S05/S06; no new tracker. Root owns allshell/DI/nav/sharedsource. Accepted publicLocalDeckDrafts/DeckDraftPlanner, schema15/CLDF/framing/namespace; isolatedcompleteSQLproducer active undercanonicalclaim, realAppGraphbinding helduntilproduceraccepted. No fakeadapter/partialbinding. Architecture reviewed actualAstra campaign_engineering_review after readingrealcontroller/dialog/navigation/ports; implementation/runtime review stillrequired.

## Actual source and chosen lane

Current DeckLibraryController.editorChange blocksduringrequests/busy; attempt unconditionallyclearseditor onack; select/clear/create/duplicate/versions/topnav have no rawflush; no lifecycleflush. Keep list/create/duplicate/ordinarylibrary machinery, add dedicated retained DeckDraftEditorController ownedbylibrarycontroller. Neverroute autosave throughlegacyPatch attemptcompletion. Reuse actualDeckEditorForm/fieldUIwherecoherent, splittextediting from structuralcontrols andrawstatus fromcontentstatus.

Appendoptional complete localDeckDrafts+deckDraftPlanner ShellPorts fields (rootDIRTY declarationonly, unbound untilactualacceptedproducer). Enable together; ShellControllers passescompletecapabilities, actualAppGraph laterconstructsacceptedSqlLocalDeckDrafts andLocalDeckDraftPlanner. No shell→persistence implementationdependency.

## Retained state and IO invariants

Onequalifieddeck/formtarget/sessionticket; latestrawbody/localeditsequence, acknowledgedDeckDraftState/token, exactbasesnapshotonlywhenknown. One mutation lane serializesfrozenwrite/discard/preparedrequest andliveactually-submittedcommitflag. Keep one latest queuedrawvaluewhileIO. Cancelobsoletetimers/readtickets, NEVERsubmittedmutations. Uncertainoperations exactretrybeforeasuccessor. Texttypingallowedwhilewrite/commit; structuralADDtarget/cardcontrols freezeoncepreparationbegins. No queuedinputclear onack.

Monotonic firstdirtydeadline: trailingdelay capped500ms fromfirstoutstandingedit; continuousinputcannotresetcap. Ifstoragebusy retainoverdueflush/drainimmediatelyafter, notdisklatencypromise. AfterackrawderiveDETAILS/ENTRYTEXT offUIthread viaactualplanner; no-progress invalid/staged-only notbackendcall. FreezeoneopID/ADDentryID perproposal, prepareexactacktoken/canonicalpatch, commitreturnedintent/hash. ADD/REMOVE/stagedboard-finish-categories explicitonly. Emptypatch resultrevisionunchanged, nolegacybase+1assumption.

Ack validatesoperation/request/source/fullpatchprojection/effect. Onlysamelive session/target owningtheack canCASrebasequeuedinput; preservefieldseditedsincecapturedsource exactly, updateunchangedconsumedfields fromack. OwnADDqueuedform transitionsENTRY atfrozenID, neversecondADD. External/restartedPreservedLater notautomaticrebasingauthority. Freshcatalog proof immediatelybeforeprepare AND freshcommitdispatch viaactualcurrentDeckCard; revivedPREPARED notproofsubmitted. Onlyretainedliveexactsubmitteduncertainrequest bypassesrenewedcatalogproof. Recoveredselectionidentity/generation separatefromephemeralCardRecordauthorization.

## Recovery and exit gate

Loadbothrawstate+currentlibraryhead withreadtickets; separatenonatomicreads stillrequirebackendCAS. APIdoesnotexposearbitraryhistoricalbase: ifraw.base!=currenthead, displayexactpreservedraw conflict/disablepromotion, neversynthesizehistoricalbase byrewritingrevisionontodayssnapshot. COMMITTEDdiscover historicalack withoutcatalogthenreadcurrentstateseparately. Discoverwhenopeningdeck, noglobalenumerationclaim.

Single retainedtypedexit target/ticket for select/back/newedit/create/duplicate/versions/topnav. Flushlatestraw+eligibletext, thenreleaseorvisiblefailurechoice. Continue retainsdurablerecovery anduncertainoperation; neverlegacyabandon toforget request. Explicitdiscardactualmode/generation/pendingID. Suspend/routethroughgate undo/reorder/restore duringunresolvedlane. Rootnavigationmustnotbypassgate; callbacks/ticketsmustsurviveconfigurationwithoutstale navigation. Best-effortbackgroundflush atstableshellowner, resize neitherflushnornewcontroller. ViewModeldestructioncaninterrupt; restartreconciliation determinesdurableoutcome.

## Coherent verification boundary

Schedulercontinuousinput500maxwait; invalid/lonesurrogaterecovery; typingduringwrite/commit; ackloss allstages; emptyalias; own/externalPreservedLater; oneADDID/liveENTRYtransition; restartedPREPAREDvsCOMMITTED; catalogchangebetweenprepare/dispatch; exitfailure/continue/durableexplicitdiscard; restore-race; adaptiveconfigurationretainsqueue/session. RealAppGraph/Room UIjourney andindependentreopen afterproducerintegration, strings/design/accessibility/focus meaningfulfeaturegate. Nativecompilation/execution/physicalFold7 distinct; finishedAPKholdunchanged.

Next root: implementretainedcontroller+focusedrealisticportfixturetests, actualUIbindings/navigation/lifecycle thenacceptedrealSQLDI integration. No per-editfullsuite/test-firstritual; batchcoherentstate-machine/source review beforefocusedgate, assembledriskgateafterrealproducer accepted. Preserveexistinglegacytests/regressions. Source-onlyarchitecture/declarations do notestablishnewuserworkflowyet.

Boundedfirstrootprerequisite accepted: ShellPorts appended2optionalunboundfields +DeckDraftQueuedInput purefieldmerge/source12tests, independent49combinedtests4XMLACK39s incl26transfer/11legacyeditor. This is notactor/UI/autosaveacceptance. Editedmeanslatestvaluedifferscaptured, notfieldediteventhistory; callerproofmandatory. See PLAIN-DECK-CODEC-LOCAL-20261015.md.


Actual source dependency refinement: shell depends on core/model+common only, not feature/decks. Root adds optional deckPatchProjection callback matching DeckPatchReducer.propose, injected eventually by AppGraph, following inventoryProjection. ActualAstra architectureACK: enable durableeditor onlywithdrafts+planner+projection; runfullACKprojection offUIthread usingackmodifiedAt/compareentiresnapshot, separatelyverifyoperation/token/effect. No core/backendbaseline change. ExistingShellDestination relocatedunchanged intosharedshellpackage, two testimportsupdated; retainedDeferredDeckAction values referenceit, no storedComposecallbacks. These new sourcechanges/actorstate are UNCOMPILED/UNACCEPTED untilcoherentactor/UIgate.

Actor packet source/READY ACK after two recovery clarifications, exact statea962e422. Actual boundary compilation SUCCESS42s: shell production/unit/instrumentation Kotlin all executed, 6source fences unchanged, NO runtime/device execution. Concrete actor subdivided to native isolated producer; root retains all existing UI/library/ports/DI/navigation/lifecycle and actual acceptance.
