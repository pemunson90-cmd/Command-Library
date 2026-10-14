# Independent API27 locator correction review

Reviewer: fresh-context gpt-6-astra, high, 2026-10-13. Exact source16bd5358313c9516e0a53ebfe0ff8e1562715b43 and handoff f8741ed990e9e7f89493528908697b77c5f78338. APPROVE bounded correction; no blocking findings.

SDK>=27 guard correctly limits public O_CLOEXEC access; API23–26 retain O_RDONLY|O_NOFOLLOW|O_NONBLOCK. No hidden/numeric replacement, minSdk change or suppression. Design B atomic API30+ fcntlInt(F_DUPFD_CLOEXEC,0)/immediate PFD.adoptFd remains unchanged, as do identity checks, monitor, acquisition pin, cancellation/result semantics and synchronous cleanup.

Independent comparison against installed SDK37 data/api-versions.xml confirmed all checked handoff introductions, including O_CLOEXEC/F_DUPFD_CLOEXEC27, fcntlInt30, static PFD.dup(FileDescriptor)/adoptFd13. Legacy temporary23–26/final23–29 descriptors have no CLOEXEC guarantee. Whole-process no-exec/subprocess/untrusted-code policy remains binding; modern CLOEXEC does not revoke copied FDs or prevent fork-only inheritance.

Actually verified by reviewer: exact diff/handoff/provider/locator context, local public API metadata, source whitespace. Reviewer compilation/lint/JVM/native/Fold7: not_run. Root subsequently reproduced baseline catalog lint NewApi error at provider156 in local Gradle preflight, before integrating reviewed source. Corrected local lint/native acceptance remains separate evidence; no complete v1 claim.
