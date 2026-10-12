# Independent disk-backed catalog review

Root reviewed worker6931456 against prior LocalCatalog/ScryfallCodec, exact sameCanonicalGameData semantics, catalog primary keys, accepted activation/cleanup and native regression cases. Source inspection; hosted regression remains pending.

Verdict: source acceptable candidate. Each bounded batch reads its own transaction writes, so duplicate printing IDs and nonadjacent canonical conflicts are detected within/across batches. PK lookups return at most one stored representative; exact semantic comparison is retained rather than digest equality. Canonical rows/faces/FTS are inserted only once. Counts advance after committed batches and final actual SQL counts gate activation. Batch failure rolls back current writes; staged cleanup preserves the previous active generation. Late-cancellation protection remains intact.

Corpus-sized identity map/set is removed; only bounded record batches and one canonical decode remain. No claim of full-feed memory/time acceptance: reader chunk size, data/source semantics, disk/WAL growth, full-feed caps, cleanup/retired retention and missing Oracle-ID policies remain distinct design gates. Existing K2 caps unchanged.

Native regression tests cover duplicate/conflict behavior in1/128-record batches, decimal-value and printing-metadata equivalence, and cancellation during real staged writes. Required acceptance: actual hosted native execution, existing cancellation/reopen/search tests, and final combined matrix. No full-corpus coverage or measured performance claim.
