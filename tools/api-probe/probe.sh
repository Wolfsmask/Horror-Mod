#!/usr/bin/env bash
# Prints public signatures of the classes listed in classes.txt, from the real game jars.
# Each line: <fully.qualified.Class> [optional grep -E filter]
set -u
CP="$1"
while IFS= read -r line; do
	[[ -z "${line// }" || "$line" == \#* ]] && continue
	vis="-public"
	if [[ "$line" == @protected* ]]; then vis="-protected"; line="${line#@protected }"; fi
	if [[ "$line" == @code* ]]; then
		# "@code <Class> <method regex>": bytecode of the matching methods (which children a model looks up, etc.)
		line="${line#@code }"
		cls="${line%% *}"; meth="${line#* }"
		echo "=== $cls (code: $meth)"
		javap -c -p -cp "$CP" "$cls" 2>&1 | awk -v m="$meth" '
			/^  [^ ].*\(.*\);$/ || /^  [^ ].*\{\};$/ { on = ($0 ~ m) }
			on { print }' | grep -vE '^\s+[0-9]+: (aload|astore|dup|return|iload|fload|fstore|pop)' \
			| sed -E 's/net\.minecraft\.//g' | head -120
		continue
	fi
	cls="${line%% *}"
	filter=""
	[[ "$line" == *" "* ]] && filter="${line#* }"
	echo "=== $cls ($vis)"
	out=$(javap $vis -cp "$CP" "$cls" 2>&1)
	if [[ -n "$filter" ]]; then
		out=$(printf '%s\n' "$out" | grep -E "$filter|(class|interface|enum|record) " || true)
	fi
	printf '%s\n' "$out" | grep -v '^Compiled from' \
		| sed -E 's/net\.minecraft\.//g; s/java\.(lang|util|util\.function)\.//g; s/com\.mojang\.//g; s/net\.fabricmc\.fabric\.api\.//g'
done < "$(dirname "$0")/classes.txt"
