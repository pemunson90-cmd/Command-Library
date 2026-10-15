# Application-owned plain deck list — proposal, not accepted API

Existing execution.transfer-codecs, B08/I01, S08/S17. Root owns public types/build; eventual worker owns only new pure implementation/tests after independent contract review. No aggregate TransferService binding, catalog resolution, network acquisition, database writes, vendor-compatibility or full-backup claim.

## Evidence and scope

Read-only collection_ready_probe at86cf3f543826028372ffa034f370c3756ade9a0c found no standalone genuine vendor-export fixtures in repository paths including hidden filenames, excluding Git/build/cache. This does not establish machine-wide absence. Existing CsvCompatibilityProbeTest is synthetic dependency evidence only. R16 official Arena excerpt supports basic positive quantities/punctuation-bearing names; ManaBox example supports one printing selector, not complete grammar. Browser-flattened Goldfish/TappedOut text is not byte-faithful; printed Goldfish CSV has inconsistent field counts. RISKS/IMPOSSIBLE retain authentic fixture gaps. No reconstructed authentic fixtures, new acquisition or publication permission inferred.

Provenance pins: R-16-deck-formats.md c37f4f8686ca78751fda543770f5eabc5530d147a9fd3e06fa94c3e938e0222f; FIXTURE_POLICY.md bbbb15fdf1247a917f45e328593444948612e4acb84fe6ff6a3bbe2cfa728676; TransferContracts.kt 02c425fc952f31fb9a8351bbe00db6cb7a27d6713642a96b4cd84208af566b55. Existing Moxfield automated permission barrier/Archidekt terms conflict remain; user-provided export fallback preserved. Synthetic application-grammar oracles do not establish vendor provenance.

## Proposed complete narrow boundary

PlainDeckListCodec.parse(TransferInput,CancelToken):Outcome<PlainDeckList>, suspend; render(DeckSnapshot,CancelToken):Outcome<PlainDeckListExport>, synchronous bounded pure operation. Reuse TransferInput/RowLocation/ImportIssue/Outcome/SnapshotBytes. No ImportPayload.DeckCard until actual identity resolution.

New DTO proposal: PlainDeckListEntry(quantity:Int,name:String,board:BoardRole); PlainDeckListRow(location,rawText,entry?,issues); PlainDeckList(inputId,rows,complete); PlainDeckListLoss(entryId?,typedField); PlainDeckListExport(bytes,losses). Complete means entire bounded input consumed without row syntax errors, never identity/import readiness. Blank/header rows retain decoded raw content, null entry/no issues. Source byte offsets count original input bytes; physical rows one-based. Exact line terminators are not promised by this logical representation.

## Recommended grammar and bounds

Strict UTF8, optional leading BOM, LF/CRLF; malformed Unicode/lone CR are typed failures without partial document. Optional first line `Command Library Deck List 1`; recognized application marker with unknown version UNSUPPORTED. Initial MAIN. Exact headings Commander/Partner/Background/Companion/Deck/Sideboard/Maybeboard map all7 existing BoardRoles; these are application-defined, no product scope expansion.

Entries: canonical positive ASCII integer1..Int.MAX_VALUE then one-or-more ASCII space/tab and nonblank name. Preserve full name including trailing whitespace, punctuation, // and parenthesized text; separator whitespace excluded. No inferred x/comments/set/finish/tag syntax, normalization, merging or catalog lookup. Unknown nonblank lines become explicit issues, fully consumed document complete=false. Renderer marker+LF/fixed board order/heading/one space/original within-board row order/final LF. Global interleaved cross-board order change disclosed.

Proposed fixed caps input/output8MiB, entries10000, physical lines20000, line64KiB, name16KiB, displayName1KiB strictUTF8. These are admission limits, not measured performance. Validate stream sequence/offset/final exactly; expectedBytes never authorizes ignoring actual bounds. Stream details are fixed by the final dispositions below. Reject oversized stream before accumulation, propagate cancellation with bounded scans.

Renderer must fail missing/unknown/blank/multiline/unrepresentable originalName; never substitute an ID or omit a row. Preserve quantities/boards/names. Typed loss categories cover qualified identities/language/finish, entry IDs/notes/labels/role overrides, cross-board order, deck metadata/times/revision/catalog/category registry/package lineage. Exact loss inventory is fixed against actual DeckSnapshot by final dispositions below. Full native history/settings/versions backup remains separate.

## Executable acceptance before dispatch

Independent parse expected objects and render exact-byte goldens, all boards/punctuation/multibyte/BOM/LF/CRLF/empty/blanks. Every byte split of representative UTF8/BOM/CRLF yields same coordinates/rows. Overflow/zero/negative/fraction/x/unknown header/loneCR/malformedUTF8/unknownversion/everycap/stream-contract violations/cancellation. Duplicate rows and unresolved names retained. Parse(render(snapshot)) equals documented projection, supplementing independent oracles; rich snapshots exact loss sets, missing-name failure, deterministic bounded output. No actual import UI/database acceptance from this pure capability.

Root next: settle stream/error/loss details and independent public-boundary review; then add accepted core types/minimal feature:transfer module using existing pins and dispatch verified isolated new-file worker packet. No worker claim/API/module is ready from this proposal alone.


## Root dispositions after independent boundary review (architecture ACK; public source review pending)

Adopt inventory_journal_review corrections; no product question needed. Quantity exactly[1-9][0-9]* checked<=Int.MAX_VALUE. Parser maximalASCIIspace/tab separator; renderer rejects leadingASCIIspace/tab, CR/LF, blank, malformedUnicode, over-limit/Unknown/Absent originalName, never trims. Header-looking names afterquantity remainnames.

Stream: Known expectedBytes exact0..8MiB preflight/equality at successfulnormalflowend; Absent/Unknown ignored, noallocationfromexpectedsize. Sequence0contiguous+1, offset0contiguousactualbytes; nonfinalchunks nonempty, exactlyonefinal possiblyempty; emptyflow/postfinalchunk invalid. Awaitnormalupstreamcompletion afterfinal; trailingreadfailure prevents success. Sizecheckbeforecopy. Metadataignored transportenvelope/noauthority; mediaTypeignored/nosniffing. Cancellationincludingupstream rethrows; sanitizedreadexception TRANSPORT (notfatalJVMerrors); malformedframe/UTF8/bounds INVALID_INPUT; canonicalfuturefirstlinemarker UNSUPPORTED; ordinaryrowerrors ERROR/complete=false.

Rows: striponeBOM onlybyte0; includeBOMbytes insourceoffsets, firstphysicalrowoffset0. Empty/BOMonlyinput zerorows. Finalterminator nophantomrow; consecutive terminators blankrows. rawText excludesBOM/terminators. Line64KiBcontentbytes excludesdelimiters; name16KiBincludestrailingwhitespace. Marker exactfirstphysicalline only; canonicalpositiveversion!=1 UNSUPPORTED; malformed/repeated/misplacedmarker rowissue. Headings exact/untrimmed; whitespaceonlyrows retainedblank. All otherinvalidlines nullentry/staticERRORissue/complete=false. Encoding/admissionbounds returnnopartialdocument. Stableissuecodes: INVALID_QUANTITY for digit/sign-leading invalidquantityprefix, INVALID_ROW forotherunrecognizedsyntax, INVALID_MARKER formalformed/repeated/misplacedapplicationmarker. Quantityoverflow INVALID_QUANTITY. Oversizename/line/document failure notrowissue.

Fixed renderer boardorder COMMANDER,PARTNER,BACKGROUND,COMPANION,MAIN,SIDEBOARD,MAYBEBOARD, headingonlynonemptyboards. Boundedprojectionadmission notrecursivefullgraphvalidation; omittedpackage/catalog/evidence nottraversed. RejectduplicateentryIDs, preserveduplicatesame-namerows. Bounds representedstrings/entrycount/output/losses beforeunboundedallocation.

Typedlossenum declaration/order: DECK_ID,DECK_NAME,FORMAT,DESCRIPTION,FOLDER,TAGS,CREATED_AT,MODIFIED_AT,REVISION,BUDGET,PROFILE_ID,CATALOG_REFERENCES,PACKAGE_INSTANCES,PRIMER,CATEGORY_ORDER,ENTRY_ORDER,ENTRY_ID,ORACLE_ID,PRINTING_ID,RAW_IDENTITY,LANGUAGE,FINISH,NOTES,LABELS,ROLE_OVERRIDES. Decklosses fixedenumorder thenentries originalorder/fixedfieldorder. AlwaysreportIDs/deckname/language/timestamps/revision and everyomittedKnowledge fieldincludingAbsent/Unknown/reason/evidence. Nullable/collection/text whenpopulated; categoryOrder nonnullINCLUDINGempty reports loss. ENTRY_ORDER onlyif stableboardgrouping changesactualentryIDsequence. OriginalName preservedexactlyor renderfails, no losscategory. Losses bounded by16deck+9perentry (IDsbounded1024strictUTF8,duplicatecheck); output8MiB/entries10000. Deckname alwayslost since listmarkercontainsnoname; blankname stillanomittedvalue.

Independent falsification required: leadingnamewhitespace/headerlike names, splitBOM/UTF8/CRLF, missing/duplicatefinal/trailingupstreamfailure/wrongknownlength, explicit-emptycategoryregistry/Unknownidentityevidence/interleavedboards/duplicatesame-name. NativebackupI01/vendorcoverage remainseparate.


ActualAstra inventory_journal_review architectureACK refreshed845da9ac69b6fee0393f26fe080d25867ad8941c18f294f2b4391b4563c9d5b0 afterallpriorHOLDsresolved. Packetclarifications: bytepartitionoracles everyLEGALpartition, empty nonfinal invalid; RowLocation.sourceName=input.displayName,fieldName=null forlineissues, physicalrowlocation attachedtoeveryissue. This is no implementation/vendor/fullI01acceptance. Root mayfreeze narrowtypedAPI and dispatch afterpublicsourceACK.


Roottechnicalfutureversionprecedence clarified afteractualsource review: oncecanonicalfirstphysicalfuturemarkerrecognized, remainingbodyopaque tov1quantity/name/entrycountgrammar. Continue generic8MiB/physical20000lines/64KiBline/strictUTF8/streamframing+normalupstreamcompletion; transport/framing/globaladmissionfailure wins, otherwisereturnUNSUPPORTED. Do notapplyv1name16KiB/entry10000limits tofuturebody. Beforefirstbuildworkerrepairs/adds16385bytefuture-nameandtrailingtransportoracles, priorprebuildpinsretained. Noexecutedfailure.
