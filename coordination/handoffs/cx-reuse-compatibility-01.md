# Latest continuation delta
Branch: agent/cx-reuse-compatibility-01; source/evidence commit: cda7cc73cc4c972b945c1a2068aa4a99d8736845.
Root-only dependency commit2235e5e cherry-picked as990975f; worker changed only five owned paths.
Recommend test-only com.jsoizo:kotlin-csv-jvm:1.10.0 and Coil core/compose-core3.3.0; aliases already root-integrated.
Actual K2/std2.2.10 compilation passes; CSV JUnit8/8 and Coil real cache LRU/pack preservation JUnit1/1 pass.
Latest Coil3.6.3 rejected: published stdlib2.4.10 metadata incompatible; CSV2 comparison passes but runtime upgrade deferred.
Four Android runtime probes compiled, not_run; AGP/full graph/Room-SQLite-DataStore coexistence/device/Compose/native graph not_run.
Artifact/source/POM/module hashes, license/tag provenance, transitive metadata and finite red/green evidence: PROBE-DECISIONS.json.
Next: root independently review both owned probe classes/evidence, run hosted Android/common tests and graph checks; then decide production pins.
