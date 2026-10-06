#!/usr/bin/env bash
# Checks the jar a player installs, not the dev build every other test runs against.
# Where the game's own names are obfuscated (1.x), the mixin targets must be remapped to
# intermediary names (in a refmap, or in the mixin classes themselves); otherwise every dev test
# passes while players crash, or quietly get no fog.
# Usage: tools/ci/check-jar.sh <mc>
set -u
MC="$1"
JAR=$(ls build/libs/occupant-*.jar 2>/dev/null | grep -v sources | head -1)
[ -z "$JAR" ] && { echo "no jar in build/libs"; exit 1; }
echo "jar: $JAR"
CONFIG=$(unzip -p "$JAR" occupant.client.mixins.json) || { echo "no mixin config in the jar"; exit 1; }
echo "$CONFIG"
echo "$CONFIG" | grep -q '"JAVA_[0-9]*"' || { echo "the mixin config's Java level was never filled in"; exit 1; }
case "$MC" in
	1.*) ;;
	*) echo "unobfuscated version: the game's own names are the real ones"; exit 0 ;;
esac
TMP=$(mktemp -d)
unzip -q -o "$JAR" -d "$TMP"
for f in "$TMP"/*refmap*.json; do
	[ -f "$f" ] || continue
	echo "--- $(basename "$f")"; head -c 1500 "$f"; echo
	grep -q "class_[0-9]" "$f" && { echo "the refmap maps to intermediary names"; exit 0; }
done
if strings "$TMP/com/wolfsmask/occupant/mixin/client/FogRendererMixin.class" | grep -q "class_[0-9]"; then
	echo "the mixin class itself was remapped"; exit 0
fi
echo "the mixin targets were not remapped: players would crash, or get no fog"
exit 1
