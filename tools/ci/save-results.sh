#!/usr/bin/env bash
# Saves test results (logs, screenshots, probe output) to the ci-results branch, never to the
# branch being built: that branch holds only the mod and its download.
#
#   save-results.sh "<message>" <folder in ci-results> <file or folder>...
#
# A folder given as "dir/." has its contents copied. With CLEAR=1 the destination folder is
# emptied first.
#
# ci-results keeps no history: each save replaces it with one commit holding the latest of
# everything, so old screenshots do not pile up in the repository. Many jobs finish at once, so the
# replacement only goes through if nobody else saved in between (a lease on the branch's tip);
# otherwise it starts again from what they saved.
set -u
msg="$1"
dest="$2"
shift 2
BRANCH=ci-results
sources=()
for f in "$@"; do
	if [ -e "$f" ]; then sources+=("$(cd "$(dirname "$f")" && pwd)/$(basename "$f")"); fi
done
W="${RUNNER_TEMP:-/tmp}/results-$$"
IDX="${RUNNER_TEMP:-/tmp}/results-$$.index"
cd "${GITHUB_WORKSPACE:-.}" || exit 1
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
for attempt in 1 2 3 4 5 6 7 8 9 10; do
	rm -rf "$W" "$IDX"
	mkdir -p "$W"
	git update-ref -d "refs/remotes/origin/$BRANCH" 2>/dev/null
	tip=""
	if git fetch -q --depth 1 origin "+refs/heads/$BRANCH:refs/remotes/origin/$BRANCH" 2>/dev/null; then
		tip=$(git rev-parse "refs/remotes/origin/$BRANCH")
		GIT_INDEX_FILE="$IDX" git --work-tree="$W" read-tree "$tip"
		GIT_INDEX_FILE="$IDX" git --work-tree="$W" checkout-index -a -f
	fi
	if [ "${CLEAR:-0}" = "1" ]; then rm -rf "${W:?}/$dest"; fi
	mkdir -p "$W/$dest"
	for s in "${sources[@]}"; do cp -r "$s" "$W/$dest/"; done
	GIT_INDEX_FILE="$IDX" git --work-tree="$W" add -A -f .
	tree=$(GIT_INDEX_FILE="$IDX" git --work-tree="$W" write-tree)
	commit=$(git commit-tree "$tree" -m "$msg [skip ci]")
	if git push -q --force-with-lease="refs/heads/$BRANCH:$tip" origin "$commit:refs/heads/$BRANCH"; then
		break
	fi
	sleep $((attempt * 2))
done
rm -rf "$W" "$IDX"
