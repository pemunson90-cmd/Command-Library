# Genuine rules parser probe

Read-only validation of the existing Kotlin rules parser against the locally acquired official English rules TXT. No repository files were edited. The source payload remains under `/workspace/scratch/k3-genuine-inputs`; this report records only provenance and aggregate results.

## Input identity

- Source: Wizards official rules download, `https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt` (linked from `https://magic.wizards.com/en/rules`).
- Local file: `/workspace/scratch/k3-genuine-inputs/wizards-comprehensive-rules-20260925.txt`.
- Acquisition manifest: `/workspace/scratch/k3-genuine-inputs/evidence-manifest.json` records HTTP 200, `text/plain`, 977,752 actual bytes, strict UTF-8 decode success, and SHA-256 `8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca`.
- The downloaded file's independent `sha256sum` matched the manifest and the parser's exact-byte hash. Source version/effective date remained unknown in the parser probe metadata; the date in the download filename was not promoted to metadata.

## Actual parser run

Compiled repository `core/model` Kotlin sources and all production Kotlin files under `feature/rules/.../parser` together with a temporary same-package `GenuineRulesProbe.kt`. This let the probe invoke the actual internal `RulesTextParser`; it did not reimplement parsing. The probe used Kotlin compiler 2.2.10 embeddable, the repository's coroutines 1.10.2, Java 21, and JVM target 17. Compiler/dependency jars and probe classes are in `/workspace/scratch/kotlin-probe`, outside the repository. No Android SDK or Gradle/Android build was used.

The probe supplied two fresh file streams: an index pass and a parse pass. Each stream emitted contiguous 32 KiB `ByteChunk`s with one final chunk, using at most one-chunk lookahead, and the parser's default `RulesParserLimits`. It supplied the true Wizards source URI and left version/effective date unknown. The callback retained only identifier strings for a duplicate check and per-kind counters; it did not print or save rule text.

Compiler invocation (source lists were all production `*.kt` files in the directories named above):

```sh
java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 \
  -classpath "$LIB/kotlin-stdlib-2.2.10.jar:$LIB/kotlinx-coroutines-core-jvm-1.10.2.jar:$LIB/annotations-13.0.jar" \
  -d "$PROBE/classes" $MODEL_SRC $RULES_SRC "$PROBE/GenuineRulesProbe.kt"
```

Runtime invocation:

```sh
java -cp "$PROBE/classes:$LIB/kotlin-stdlib-2.2.10.jar:$LIB/kotlinx-coroutines-core-jvm-1.10.2.jar" \
  com.architectpsg.commandlibrary.feature.rules.parser.GenuineRulesProbeKt \
  /workspace/scratch/k3-genuine-inputs/wizards-comprehensive-rules-20260925.txt
```

Observed parser result:

```text
result=OK
byte_count=977752
sha256=8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca
indexed_identifiers=4355
entries=4355
entries_by_kind=NUMBERED=3319,GLOSSARY=741,EXAMPLE=277,NOTICE=18
references=2075
unresolved_references=5
duplicate_identifiers_parser=0
duplicate_identifiers_callback=0
anomalies=0
```

This establishes that the current parser's two passes accepted these exact locally held bytes under its default limits and agreed on byte count and SHA-256. The five unresolved references are parser aggregate output; this probe intentionally did not retain or report target text. The result is one genuine-file validation and does not establish behavior for other editions, complete semantic correctness, source redistribution permission, or an Android import/build result.

## JVM compile and focused JUnit run

A second scratch compilation used the current repository sources, now including `core/common` and all `feature/rules` production files (`LocalRules.kt`, `RulesCodec.kt`, `RulesCursor.kt`, SQL, and parser code). It also compiled these requested test classes in the same invocation:

- `feature/rules/.../RulesCodecTest.kt`
- `feature/rules/.../RulesCursorTest.kt`
- `core/model/.../LocalRulesContractsTest.kt`

Dependencies were Kotlin 2.2.10, coroutines 1.10.2, Gson 2.13.2, JUnit 4.13.2, Hamcrest Core 1.3, and JetBrains annotations 13.0. Runtime was Java 21 with JVM target 17. No Android SDK was used.

The compilation command (variables expand to the listed production directories and three test files):

```sh
java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 \
  -classpath "$CP" \
  -d "$PROBE/current-classes" $MODEL_SRC $COMMON_SRC $RULES_SRC $TESTS
```

JUnit invocation and output:

```text
java -cp "$PROBE/current-classes:$LIB/kotlin-stdlib-2.2.10.jar:$LIB/kotlinx-coroutines-core-jvm-1.10.2.jar:$LIB/gson-2.13.2.jar:$LIB/junit-4.13.2.jar:$LIB/hamcrest-core-1.3.jar" \
  org.junit.runner.JUnitCore \
  com.architectpsg.commandlibrary.feature.rules.RulesCodecTest \
  com.architectpsg.commandlibrary.feature.rules.RulesCursorTest \
  com.architectpsg.commandlibrary.core.model.LocalRulesContractsTest
JUnit version 4.13.2
..........
Time: 0.152

OK (10 tests)
```

## Genuine card mapper probe

The production `feature/catalog/.../ScryfallCodec.kt` and a temporary same-package main were compiled into scratch classes and the mapper ran against `/workspace/scratch/k3-genuine-inputs/scryfall-time-vault-card.json`. The current acquisition manifest records Scryfall's named-card URL, 4,968 bytes, HTTP 200, and SHA-256 `51ac804f34d76edda2aad7d7f984312b4cce7806d5d9fec5ba42cd60049f8e32`; the local file hash matched. The mapper received the true Scryfall URL and unknown source/effective dates.

```text
result=OK
byte_count=4968
sha256=51ac804f34d76edda2aad7d7f984312b4cce7806d5d9fec5ba42cd60049f8e32
card_name=Time Vault
language=en
oracle_id=99d4d99d-cf56-45aa-aa39-a250695612f2
printing_id=c367ffc1-8084-45a1-87d5-22183604d1cb
face_count=0
supplied_link_count=9
```

This establishes a genuine single-card mapping through the current mapper only. It does not establish coverage of full catalog feeds, every layout, another language, product import, rights, or Android integration.

## Separate Kotlin module-boundary validation

After hosted compilation exposed a cross-module smart-cast failure in `LocalRules.search(query.corpusId.value)`, the root fix at repository HEAD `0c58cfb` stores the request value in a local before the branch. To validate the Kotlin module boundary rather than compiling every source as one module, current sources were compiled independently into scratch class directories (each with its own Kotlin module metadata):

1. `core/model` production sources compiled alone.
2. `core/common` production sources compiled against the model output directory.
3. All `feature/rules` production sources plus `RulesCodecTest.kt` and `RulesCursorTest.kt` compiled against the separate model and common output directories.
4. `LocalRulesContractsTest.kt` compiled separately against those outputs and the rules output.

The JUnit run used the three previously listed focused classes and returned:

```text
JUnit version 4.13.2
...........
Time: 0.172

OK (11 tests)
```

The exact module compile forms were:

```sh
java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -module-name core_model \
  -classpath "$CP" -d "$PROBE/module-check-20261012/model" $MODEL_SRC

java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -module-name core_common \
  -classpath "$PROBE/module-check-20261012/model:$CP" \
  -d "$PROBE/module-check-20261012/common" $COMMON_SRC

java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -module-name feature_rules \
  -classpath "$PROBE/module-check-20261012/model:$PROBE/module-check-20261012/common:$CP" \
  -d "$PROBE/module-check-20261012/rules" $RULES_SRC $RULES_TESTS

java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -module-name core_model_test \
  -classpath "$PROBE/module-check-20261012/model:$PROBE/module-check-20261012/common:$PROBE/module-check-20261012/rules:$CP" \
  -d "$PROBE/module-check-20261012/model-test" "$LOCAL_RULES_CONTRACT_TEST"
```

This is distinct from the earlier combined compile, which reported 10 passing tests before the hosted boundary check. The strict current-source compile and test pass reported 11. This confirms the cross-module fix at `0c58cfb` for the scoped JVM sources; it remains separate from Android/Gradle integration. The execution image lacks the `jar` utility, so compiler-produced module directories were used directly as separate classpath artifacts instead of packaging JARs.

## Pre-fix unresolved-reference diagnosis

A pre-fix run at HEAD `0c58cfb` used the actual parser with a temporary callback that emitted only owning IDs, short raw references, and target IDs. A direct trace of `RulesStructure` checked the relevant LF-delimited source line in its region and open-entry state. No rule prose was retained in the report. The pre-fix parser reported five unresolved reference occurrences across four unique target IDs:

- `608.2`: raw `608.2c–m` leaves target `608.2l` unresolved; the exact target string does not occur anywhere in the official file.
- `glossary:Evasion Ability`: raw `509.1b–c` leaves `509.1b` unresolved.
- `glossary:Restriction`: raw `509.1b` leaves `509.1b` unresolved.
- `glossary:Role`: raw `111.10j–r` leaves `111.10l` and `111.10o` unresolved; neither exact target occurs anywhere in the official file.

`509.1b` is different: the official file has a candidate numbered heading on LF line 2352. The actual structure trace reported `region=BODY`, `section=5`, `chapter=509`, and current entry `509.1a`, but the current Kotlin heading regex returned false. The parser consequently retained that candidate line as text under `509.1a`; the parse reported no anomaly. The candidate line includes U+2028 LINE SEPARATOR at character offset 287. The UTF-8 line reader only separates on LF, while Java regex `.` does not match U+2028 by default, so the `.*` tail in the numbered heading patterns rejects this otherwise valid line. This establishes that `509.1b` is a parser omission in these bytes; the other three unresolved target IDs are absent from these bytes.

Arithmetic: three unresolved occurrences target three IDs absent from the raw file (`608.2l`, `111.10l`, `111.10o`); two occurrences target the same genuine `509.1b` heading that the old parser omitted. The parser fix was delegated and landed separately; this probe made no repository edits.

## Post-fix genuine rules rerun

After the Unicode regex fix landed at HEAD `32ae241`, the same official file was reprocessed by the actual production Kotlin parser in two bounded 32 KiB streams. The input SHA-256 and byte count remained unchanged. The post-fix callback retained only identifier strings and aggregates; it confirmed both known glossary references now resolve to a distinct `509.1b` entry. The three remaining unresolved target IDs are absent from the raw source.

```text
result=OK
byte_count=977752
sha256=8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca
indexed_identifiers=4357
entries=4357
entries_by_kind=NUMBERED=3321,GLOSSARY=741,EXAMPLE=277,NOTICE=18
references=2075
unresolved_references=3
duplicate_identifiers_parser=0
duplicate_identifiers_callback=0
anomalies=0
reference_status owner=glossary:Evasion Ability raw=509.1b–c target=509.1b status=resolved
reference_status owner=glossary:Restriction raw=509.1b target=509.1b status=resolved
unresolved_reference owner=608.2 raw=608.2c–m target=608.2l
unresolved_reference owner=glossary:Role raw=111.10j–r target=111.10l
unresolved_reference owner=glossary:Role raw=111.10j–r target=111.10o
```

The parser compilation used every current `feature/rules/src/main/kotlin` source plus the temporary `GenuineRulesProbe.kt`, against separately compiled `core/model` and `core/common` class directories. The JUnit runtime used the repository-declared Kotlin 2.2.10, coroutines 1.10.2, Gson 2.13.2, JUnit 4.13.2, and Hamcrest 1.3 artifacts.

The parser regression test sources `SyntheticRules.kt` and `RulesTextParserTest.kt` were then compiled with all rules production sources against those separate model/common outputs. The JUnit class run included the new `unicodeLineAndParagraphSeparatorsRemainInHeadingAndRuleTails` case:

```text
JUnit version 4.13.2
....................................
Time: 0.403

OK (36 tests)
```

Both runs used Java 21 and JVM target 17, outside Android/Gradle. The rules input and card payload remain in the private scratch input directory; this report contains no rules prose or card JSON.

## Final post-range-fix validation

At HEAD `e1d5e90` (production/test sources unchanged through current HEAD `9527c87`), I repeated separate module compilation from current sources: compile `core/model` alone, compile `core/common` against the model output, then compile all `feature/rules` production and pure JVM test sources against both separate outputs. I also compiled `LocalRulesContractsTest.kt` separately. Java 21, Kotlin 2.2.10, coroutines 1.10.2, coroutines-test 1.10.2, Gson 2.13.2, JUnit 4.13.2, Hamcrest Core 1.3, and JVM target 17 were used.

JUnitCore ran all pure `feature/rules` test classes and the focused model contract test:

- `RulesCodecTest`
- `RulesCursorTest`
- `RuleReferenceScannerTest`
- `RulesParserLimitsTest`
- `RulesParserMutationTest`
- `RulesTextParserTest`
- `Utf8LineReaderTest`
- `LocalRulesContractsTest`

Result:

```text
JUnit version 4.13.2
...............................................................................
Time: 0.636

OK (79 tests)
```

The genuine Wizards file was then run through the current production `RulesTextParser` with its two 32 KiB passes. The exact-byte hash remained unchanged. The current result is:

```text
result=OK
byte_count=977752
sha256=8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca
indexed_identifiers=4357
entries=4357
entries_by_kind=NUMBERED=3321,GLOSSARY=741,EXAMPLE=277,NOTICE=18
references=2072
unresolved_references=0
duplicate_identifiers_parser=0
duplicate_identifiers_callback=0
anomalies=0
heading_candidate_text_owner=509.1b
reference_status owner=glossary:Evasion Ability raw=509.1b–c target=509.1b status=resolved
reference_status owner=glossary:Restriction raw=509.1b target=509.1b status=resolved
```

Thus the range fix removed the three phantom unresolved targets previously produced from two ranges, with no unresolved targets in the final genuine-file run. Both glossary references to the real `509.1b` heading remain resolved. Aggregate rule text was never printed or stored; only the scratch probe code, compiled classes, source file, and aggregate outputs were used.
