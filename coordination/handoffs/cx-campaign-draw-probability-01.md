# Draw probability producer attempt01

## Latest continuation delta

Branch `agent/campaign-draw-probability-20261015`; base `4b6a8a618c365462c379158ff3ddf2b3672ea87a`. Exact three new owned paths: `LocalDrawProbabilities.kt`, matching JVM test in `android-app/feature/decks`, and this handoff. Source frozen for root review; commit awaits exact authorization.

Implemented the canonical feature-local API with exact BigInteger hypergeometric counts, all k=0..draws (including zeros), explicit input/resource rejection, immutable points, and native cancellation propagation. Computes total/feasible seed with bounded binomial recurrence and advances feasible points with one exact ratio; factors are promoted before multiplication. No core/config/schema/DI/UI changes.

Authored a coherent JUnit suite covering literal 4/2/2 and ordinary 60/24/7, 99/35/7 reference counts; empty/zero/all/none/forced support; exhaustive bit-subset enumeration for N<=7; complement/conservation; invalid/Int extremes; upper 1,000,000/500,000/1000 case; exact cancellation identity before work, inside combinations and inside point recurrence.

Verification: isolated cwd/top/branch/HEAD/common Git dir/Git environment/clean baseline confirmed; source inspected; literal reference arithmetic independently calculated using Python `math.comb`; whitespace/path/hash checks performed. JVM tests/build/compiler/lint `not_run` (root-only build slot). No runtime or physical timing claim. No full playtest, mulligan, persistence or UI completion claim; bounded B04 probability portion only of PX05-022/PX06-022.

Next action: root independent source review, exact commit authorization, then root batches focused decks tests with actual calculator consumer integration. No known blocker in producer source.
