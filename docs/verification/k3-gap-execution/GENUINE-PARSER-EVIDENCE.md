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
