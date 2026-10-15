# Independent proposed CLDP1 and edit request-v1 fixtures

Manually framed with Python struct before any production patch encoder exists. Synthetic local values; accepted design r1, independently parsed to EOF by actual Astra high. No output from production implementation used.

Empty patch has zero edits and empty reason. Rename patch has one tag0 Rename("Renamed"), reason "User rename". APPLY request uses local-decks/golden-deck expected0 and the exact rename patch. UNDO uses same deck expected1, no patch field. All lengths big-endian unsigned32; revisions signed64; headers magic plus unsigned16version1. Request kind2/3 follow existing CLDR1 CREATE0/DUPLICATE1. Header CLDP is distinct from CLDK snapshot payload.

| Fixture | Decoded wire bytes | SHA256 of decoded wire |
|---|---:|---|
| patch-empty-v1.hex | 14 | a1292810e0855e6b926cbae7150030c03bd1a9a49c1c0cacf6f369f68c29781a |
| patch-rename-v1.hex | 37 | 802320dfa9cd8ff917755274b7181d3f2eba325313b651ed687b0f09fa51272e |
| apply-request-v1.hex | 86 | be04a1397477cd86e650456b59779ca03433213d912ed77c13bc45195f34deb6 |
| undo-request-v1.hex | 45 | 3bd8ad4995520ec512f0c628aebda433116d7e6a0bafb5382384c5a13ee49977 |

Golden disagreement requires protocol investigation, never replacing expected bytes with production output. No persistence/editor/native acceptance claimed.
