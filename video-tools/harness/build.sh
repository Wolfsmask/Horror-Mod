#!/bin/bash
# Compiles the real motion code from the repo against the stand-ins, with the harness.
set -e
H="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$H/../.." && pwd)"
R="$REPO/src/client/java/com/wolfsmask/occupant/client/render"
rm -rf $H/out && mkdir -p $H/out
javac -nowarn -d $H/out $(find $H/stubs -name '*.java') $R/LegGait.java $R/OccupantPose.java $R/OccupantGeometry.java $R/OccupantFit.java $R/OccupantRenderState.java "$REPO"/src/client/java/com/wolfsmask/occupant/client/test/Person.java "$REPO"/src/client/java/com/wolfsmask/occupant/client/test/Sitter.java "$REPO"/src/client/java/com/wolfsmask/occupant/client/test/Walker.java $(find $H/src -name '*.java') 2>&1 | (grep -v JAVA_TOOL || true); test ${PIPESTATUS[0]} -eq 0
