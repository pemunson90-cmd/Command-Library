# Inventory request compatibility prerequisite

Frozen proposed CLIP1: ASCII CLIP, unsigned16bigendian version1, unsigned32 edit count (<=1024), then edit-order tags: Add0+complete CLOL1 nested OwnedLot (without nested magic); Replace1+same; Adjust2+IDstring+i32delta+reasontext; Remove3+IDstring+reasontext. All scalar/list/nested wire rules are unchanged DeckBinary/CLOL1, total8MiB and aggregate100000listitems. This codec is structural: it neither accepts a patch semantically nor grants persistence authority. Foreign metadata/order/reasons are retained exactly.

Frozen CLIR1 request hash input: ASCII CLIR +u16v1 +methodbyte (APPLY0/UNDO1), u32-length-prefixed ASCII local-inventory, signed64 expected revision (nonnegative DTO), then APPLY only u32-length-prefixed complete CLIP1 payload. Operation ID is receipt key, not part of its content digest. SHA256 lowercasehex. Request helper requires pinnedCLIP1header and byte bound BEFORE copy; the backend must pass actual validated codec output, not treat header checks as full patch decoding. Digest updates at most1024bytes with cancellation between; input SnapshotBytes copy and existing codec remain bounded/noncancellable internally.

Independent Pythonstruct fixtures were framed before tests/production encode, no regeneration fromactual:

- empty-inventory-patch-v1.hex:10bytes SHA3abdc30a717a7cba0c7418edd6ef7212865aafd5d8d484822117aebaa7a6746b.
- empty-inventory-apply-v1.hex:48bytes,expected0,SHA0f2fbe33508586a7bdfa3cac35f33c74c3c08e52b67ccfcbcd8f43a27a43dd1c.
- inventory-undo-v1.hex:34bytes,expected7,SHA815e01ab5aa9f26e147bd0147e93ae38276314199e82340027088e19cff76f46.

Exact publicLocalInventory contract uses explicit Committed/Rejected, complete consistent snapshot, permanent reserved Add IDs and grouped monotonic undo; LocalInventoryPatchCodecPort is complete both directions. Semantics ACK design2026-10-15-local-inventory-journal-design.md; actual independent Astrahigh exact-source ACK: LocalInventoryContracts ed1e85369f1bb7cd69ca0799160a5d2d76f182ddbad9399b353a8682d431be9c; LocalInventoryPatchCodec11d658d25fd5df42cacaf00c6c5c41a7629c387c31f5a8376f8517174f6225f4; InventoryRequestsf39104488becea5241c9c4c1555e2ff022dc74369b876346bee25877a358c96c. Reviewer independently parsed fixed fixtures; ran no builds/tests. No backend/schema/DI/UI claim.

Executed root offline pinned local gate :feature:collection:test plus :data:storage:testDebugUnitTest filtered to LocalInventoryPatch/OwnedLot/DeckPayload/DeckBounds/LocalDeckPatch. Attempt r1 SUCCESS27s; actual8XML63tests0failures/errors/skips (collection32 including3requesttests, storage31 including4newpatchtests). Evidence /tmp/cl-inventory-projection-01/evidence/inventory-contract-r1.log, inventory-contract-r1-xml and inventory-contract-r1-summary.json. No native/physical execution. Subsequent schema compile is separate and does not retroactively change this source/evidence.
