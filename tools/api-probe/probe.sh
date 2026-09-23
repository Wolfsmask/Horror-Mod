#!/usr/bin/env bash
# Prints public signatures of the classes listed in classes.txt, from the real game jars.
# Each line: <fully.qualified.Class> [optional grep -E filter]
set -u
CP="$1"
while IFS= read -r line; do
	[[ -z "${line// }" || "$line" == \#* ]] && continue
	cls="${line%% *}"
	filter=""
	[[ "$line" == *" "* ]] && filter="${line#* }"
	echo "=== $cls"
	out=$(javap -public -cp "$CP" "$cls" 2>&1)
	if [[ -n "$filter" ]]; then
		out=$(printf '%s\n' "$out" | grep -E "$filter|(class|interface|enum|record) " || true)
	fi
	printf '%s\n' "$out" | grep -v '^Compiled from' \
		| sed -E 's/net\.minecraft\.//g; s/java\.(lang|util|util\.function)\.//g; s/com\.mojang\.//g; s/net\.fabricmc\.fabric\.api\.//g'
done < "$(dirname "$0")/classes.txt"
