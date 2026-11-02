#!/bin/bash
# After make_tape.py has written OUT/the-occupant-long.mp4 (with chapters.txt and voice_sheet.txt beside
# it): the YouTube text, the voice-line sheet, and the copy for the repository (under GitHub's 100 MB),
# which it puts at video/the-occupant-long.mp4.
# Usage: bash deliver.sh OUT
set -e
OUT=${1:?folder the edit wrote to}
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(git -C "$HERE" rev-parse --show-toplevel)"
python3 "$HERE/yt_long.py" "$OUT" > /dev/null
{
  echo "TITLE (pick one)"
  python3 - "$HERE/yt_long.py" <<'PY'
import re, sys
src = open(sys.argv[1]).read()
for t in re.findall(r'^    "(.+)",$', src.split("titles = [")[1].split("]")[0], re.M):
    print(t)
PY
  echo
  echo "DESCRIPTION"
  cat "$OUT/yt_long_description.txt"
  echo
  echo
  echo "TAGS"
  cat "$OUT/yt_long_tags.txt"
  echo
} > "$OUT/youtube.txt"
cp "$OUT/voice_sheet.txt" "$OUT/voice_lines.txt"
# The repository's copy: two-pass, the bitrate worked out from the length so it lands near 95 MB.
SECS=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/the-occupant-long.mp4")
KBPS=$(python3 -c "print(int(95 * 8000 / $SECS - 160))")
cd "$OUT"
ffmpeg -y -loglevel error -i the-occupant-long.mp4 -c:v libx264 -preset medium -tune grain -b:v ${KBPS}k -pass 1 -passlogfile "$OUT/p" -an -f mp4 /dev/null
ffmpeg -y -loglevel error -i the-occupant-long.mp4 -c:v libx264 -preset medium -tune grain -b:v ${KBPS}k -pass 2 -passlogfile "$OUT/p" \
  -c:a aac -b:a 160k -movflags +faststart the-occupant-long-repo.mp4
rm -f "$OUT"/p*.log*
mkdir -p "$REPO/video"
cp the-occupant-long-repo.mp4 "$REPO/video/the-occupant-long.mp4"
ls -la "$OUT/youtube.txt" "$OUT/voice_lines.txt" "$REPO/video/the-occupant-long.mp4"
