# Typed card query syntax foundation

New pure JVM feature:search LocalCardQueryParser parses the documented local aliases into typed QueryAst nodes. It preserves original text and reports UTF16 half-open error positions. Boolean precedence, groups/negation, prose versus exact categorical fields, stat/color comparisons, owned/deck/price syntax and Unicode/quote escapes are bounded. Plain decimals exclude exponents; input2048codepoints/tokens256/clauses128/nesting32/number64characters are enforced.

Core additive AST nodes cover printing fields/flags/contextual clauses; existing CardSearch signatures stay unchanged and unimplemented. This parser is not DI-bound and executes no database, network or online fallback. Accepting syntax does not assert a current source, legal format, Game Changer classification, selected deck, ownership count, or pricing policy. Full indexed execution/autocomplete and A07/A08 acceptance remain incomplete.

Independent design/source ACK after concrete corrections: categorical finish:foil uses exact membership; short huge exponents rejected; blank format identities return positioned errors rather than throwing; colorless colon shorthand uses EQ(empty), avoiding universal includes(empty).

Initial focused gate SUCCESS16s:87parser/twoXML and81model/13XML tests0failure/error/skip. Corrected final parser gate SUCCESS4s:90parser/twoXML0failure/error/skip; core model source unchanged since its81pass. Logs /tmp/local-card-query-parser.log and /tmp/local-card-query-parser-r1.log. Continuity registry checks pass. No repeated whole Android or emulator gate for a pure parser; prior1113 whole and native r3d5d3026 exclude this newer unit. All fixtures explicitly synthetic. Plan docs/superpowers/plans/2026-10-14-local-card-query-parser.md.

Local commits only while GitHub rejects the configured token. No source/credential/permission bypass, PR/merge/release or finished APK claim.
