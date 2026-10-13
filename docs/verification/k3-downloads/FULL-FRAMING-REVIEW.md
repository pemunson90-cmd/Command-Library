# Independent full framing review

**Verdict: ACCEPT source candidate `90374007f20c1b42868d26ec8432c9f3c91ff739` for integration; no blocking defect found in the reviewed framing/batch scope.** Repository was clean and remained unchanged.

Reviewed actual JsonArrayFramer, JsonLinesFramer, BoundedRecordBatches, their tests, public CatalogImportLimits and handoff. Existing tiny limits remain 8 MiB input / 10,000 records / 128 KiB record. New internal full limits are explicitly capped at 2 GiB input, 500,000 records, 8 MiB record, nesting 32 and 64 KiB chunks; no public tiny capability was widened.

Both full framers check sequence/offset/final continuity, include delimiters in total-byte budgets, strictly decode UTF-8 and validate complete JSON objects/duplicate keys/nesting before delivery. Hashes cover supplied decoded bytes and remain repeatable after final input; consumers must still call finish() to establish valid stream completion. Token/coroutine checks surround byte consumption and callbacks; parsing one bounded record itself is synchronous, so device cancellation latency remains a measurement obligation.

Ordinary batches preflush before exceeding 2 MiB of UTF-8 record bytes or 1,000 records; records above 2 MiB through 8 MiB are delivered as immediate immutable singletons after prior pending records flush. Pending storage is cleared before callback execution, callbacks apply backpressure, and failure/cancellation poisons and clears the helper, preventing callback replay. Upstream framing failures require abort() and discarding the framer; staged records are not evidence of a complete artifact.

Independently reran the eight supplied compiled pure-JVM suites under `-Xmx512m`: **OK (56 tests), 2.47 seconds**. This includes exact/over record and batch boundaries, tiny-limit retention, UTF-8, malformed JSON, hash/end/continuity, cancellation and failure/no-replay cases. Worker-reported fresh compilation was not repeated by this reviewer.

No Android build/device, corpus, gzip/CRC/raw-artifact verification, DB activation or application-binding acceptance is established. A 512 MiB test heap is not a measured Android peak-memory result; raw byte batch limits also exclude mapped graphs and parser overhead. Integration must separately bound/verify compressed and decoded streams, stage batches without retaining all graphs, abort on upstream failure and activate only after complete framing/integrity validation.

Latest continuation delta: read-only source review and JVM rerun complete; only this scratch report was written. Next action: root integrates these owned sources and connects importer/writer abort and final-verification paths, then obtains hosted Android and full-source/activation evidence.
