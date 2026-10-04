#!/usr/bin/env bash
# Prints public signatures of the classes listed in classes.txt, from the real game jars.
# Each line: <fully.qualified.Class> [optional grep -E filter]
set -u
CP="$1"
while IFS= read -r line; do
	[[ -z "${line// }" || "$line" == \#* || "$line" == @mc* ]] && continue
	vis="-public"
	if [[ "$line" == @protected* ]]; then vis="-protected"; line="${line#@protected }"; fi
	if [[ "$line" == @private* ]]; then vis="-p"; line="${line#@private }"; fi
	if [[ "$line" == @versions* ]]; then
		# Which Minecraft versions Fabric supports, and which Fabric API builds exist for them.
		echo "=== Minecraft versions known to Fabric (newest first)"
		curl -s https://meta.fabricmc.net/v2/versions/game | python3 -c 'import json,sys; v=json.load(sys.stdin); print("\n".join(("%s %s" % (x["version"], "stable" if x["stable"] else "snapshot")) for x in v[:60]))'
		echo "=== Fabric API builds (newest 80)"
		curl -s https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' | tail -n 80
		echo "=== Fabric API builds for the old versions"
		curl -s https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' | grep -E '\+(1\.20\.1|1\.21\.1|1\.21\.11|26\.1)$' | tail -n 12
		echo "=== Loom versions"
		curl -s https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' | tail -n 15
		continue
	fi
	if [[ "$line" == @find* ]]; then
		# "@find <regex>": every Minecraft class whose name matches.
		pat="${line#@find }"
		echo "=== classes matching $pat"
		tr ':' '\n' <<< "$CP" | grep -E 'minecraft-(common|clientOnly|merged)' | while read -r jar; do
			unzip -Z1 "$jar" 2>/dev/null | grep '\.class$' | grep -v '\$' | sed 's/\.class$//; s#/#.#g' | grep -E "$pat" | head -60
		done
		continue
	fi
	if [[ "$line" == @jars* ]]; then
		# "@jars <regex>": which jars on the classpath match, and the classes inside them.
		pat="${line#@jars }"
		echo "=== jars matching $pat"
		tr ':' '\n' <<< "$CP" | grep -E "$pat" | while read -r jar; do
			echo "--- $(basename "$jar")"
			unzip -Z1 "$jar" 2>/dev/null | grep '\.class$' | grep -v '\$' | sed 's/\.class$//; s#/#.#g' | head -80
		done
		continue
	fi
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
