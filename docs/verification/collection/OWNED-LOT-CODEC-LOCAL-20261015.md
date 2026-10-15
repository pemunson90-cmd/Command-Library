# Complete owned-lot codec — local prerequisite

Root continuation258e3e36cb81c251871a390ff824ae1a9381b2e1 plus scoped source below. CLOL+unsigned16v1 serializes all OwnedLot fields in declaration order with unchanged CLDK1 nested field formats. Complete OwnedLotPayloadCodecPort, no persistence/catalog/merge authority. DeckWires changes only three private→internal visibility modifiers. Limits:8MiB payload, identifiers1024bytes, ordinary text1MiB, existing bounded nested lists/elements; strict Unicode/UTF8, versions/tags/enums/negative quantities/trailing data. Decode checks size before copying. Failure messages are static. No dependency/toolchain/schema change.

Independent manually framed96-byte golden and layout: minimal-owned-lot-v1.md/hex. Wire SHA2569373b3d4f810c7b95413ae1e6610e80b0d4a0ec9a412632299b49604a40393d1. Test resource identical; never generated from production encode. Tests assert encode exact bytes and decode exact complete value, rich foreign metadata/Knowledge, zero quantities, unknown versus absent, malformed/truncated/bounds, lossless finish/condition combinations.

Executed locally under pinned JDK17.0.16/Gradle9.6/AGP9.4.1/SDK37, offline, one Gradle slot:

```
:data:storage:testDebugUnitTest --tests '*OwnedLotPayloadCodecTest' --tests '*DeckPayloadCodecTest' --tests '*DeckPayloadBoundsTest' --tests '*LocalDeckPatchCodecTest'
```

Attempt r1 BUILD SUCCESSFUL1m4s. Actual4XML: OwnedLot5+DeckCodec6+DeckBounds12+LocalPatch4=27tests,0failures/errors/skips. Evidence /tmp/cl-inventory-projection-01/evidence/owned-lot-codec-r1.log, owned-lot-codec-r1-xml and owned-lot-codec-r1-summary.json. Actual Astrahigh independent source/golden/evidence review ACK; reviewer reconstructed golden separately and ran no build/tests. No native/physical/database integration claim. Root durable inventory design remains proposed and independently reviewed before public LocalInventory/schema freeze; finished APK hold remains.

Exact source ACK hashes: OwnedLotPayloadCodecPort c32ef5dc0dd2b9e07f534cd8f0c8b6b117e74e88994d09a01e7a8f14b2281e64; OwnedLotPayloadCodec147b4fffc250a16c1786448bcc401f7a268c27b05ddfea0fc79d7ceadbc0a93a; DeckWires59b52d69dbfd4e3984db7175e1470ffdac0932550c07ccb7378a115951521f66; OwnedLotPayloadCodecTest8e5999dc88e34244918d7b42b8e8b58197c9cbfa09d055bc97f52bb022182595. Golden hex text both copies1d233188ea7149a046be54ba7322d6267623ae37de1f11b068b78c1a59209840 (distinct from decoded wire SHA).
