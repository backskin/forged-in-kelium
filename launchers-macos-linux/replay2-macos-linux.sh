#!/usr/bin/env bash
# Launches KeliumReplay2 (game review) from source. Needs JDK 21 and Maven on
# PATH. First run downloads dependencies from Maven Central (needs internet);
# after that Maven caches them in ~/.m2 and later runs work offline too.
set -e
cd "$(cd "$(dirname "$0")/.." && pwd)"
mvn exec:java -Dexec.mainClass=kelium.gui.replay2.Replay2Gui
