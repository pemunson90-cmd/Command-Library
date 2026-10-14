# Explicit private acquisition permissions

Hosted frozen9a2f625/run38647074457 failed four source integration cases before any device job. Fresh local Gradle under umask0022 reproduced four/five failures52s. Artifact files/directories previously inherited the process umask; private locator guards correctly denied permissive entries.

Android artifact initialization now establishes owner-only0700 access and syncs the directory and parent. Empty file creation establishes0600 before data; append and checkpoint truncation also establish permissions before writing, so an interrupted creation cannot bypass tightening on recovery. Existing symlink/key/exact pin checks are unchanged. Permission failures propagate; no database success is fabricated. Checked File permission APIs operate on real host files and Android; the first Os.chmod attempt did not change real host modes through Robolectric's shadow and was replaced.

Independent rulings_contract_review ACK resolved its earlier HOLD on interrupted creation/recovery. Actual local fresh umask0022 gate passed49s: five actual ruling metadata/file pin/receipt/import cases and seven durable-file cases, zero failures/errors/skips in two XML reports. Added POSIX recovery test explicitly resets a partial to0644, verifies recovery0600, and checks permission preservation through append/promotion. Log /tmp/artifact-private-permissions-check-r1.log. No device execution or live publisher TLS evidence.

Combined check/lint/debug/all-test-APK boundary is running separately; no pass is claimed here before completion. Frozen failed refs stay unchanged; no hosted retry has been started.
