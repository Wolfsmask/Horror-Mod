#!/bin/bash
# Saves the new scenes' clips from every new Reel commit on ci-results (the tip is replaced each time).
# Usage: bash watch.sh WORK [how many new Reel commits to wait for]
D=${1:?work folder}/runs
mkdir -p $D
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
seen=""
want=${2:-4}
got=0
for i in $(seq 1 900); do
  if git fetch -q --depth 1 origin +refs/heads/ci-results:refs/remotes/origin/ci-results 2>/dev/null; then
    sha=$(git rev-parse refs/remotes/origin/ci-results)
    if [ "$sha" != "$seen" ]; then
      seen=$sha
      msg=$(git log -1 --format=%s "$sha")
      if [[ "$msg" == Reel* ]] && [ ! -d "$D/$sha" ]; then
        mkdir -p "$D/$sha"
        pats=()
        for s in home camp pasture well field intruder visitor doorway radio house chapel graves knock morning mine found; do pats+=("reel/$s.*"); done; pats+=("reel/found-wide.*")
        git archive "$sha" reel | tar -x -C "$D/$sha" --wildcards "${pats[@]}" 2>/dev/null
        [ -d "$D/sounds" ] || git archive "$sha" reel/sounds | tar -x -C "$D" 2>/dev/null
        echo "NEW $sha $(date -u +%T) $msg: $(ls -la --time-style=+%T $D/$sha/reel 2>/dev/null | awk '{print $7":"$5}' | tr '\n' ' ')"
        got=$((got+1))
        [ $got -ge $want ] && exit 0
      else
        echo "update $sha $(date -u +%T) ($msg)"
      fi
    fi
  fi
  sleep 20
done
