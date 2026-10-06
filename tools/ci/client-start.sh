#!/usr/bin/env bash
# Starts the real client for a version that has no client test API (CI only).
#   1. To the first screen: the gate must be drawn, with no crash.
#   2. Into a world: a dedicated server makes an ordinary world (stopped cleanly over RCON), so world generation, the mod's places included, runs as it would for a player, the client
#      opens it, and the Occupant is put in front of the player (-Doccupant.smoke=true); it must
#      be drawn, with no crash.
# Usage: tools/ci/client-start.sh <mc>. Logs: client.log, server.log, world.log.
set -u
MC="$1"
GRADLE="./gradlew -Pmc=$MC --console=plain"
crashed() { grep -qE "Game crashed|---- Minecraft Crash Report|Exception in thread \"(Render|Server) thread\"" "$1"; }
stop_game() { pkill -f "net.fabricmc.devlaunchinjector.Main" || true; sleep 5; pkill -9 -f "net.fabricmc.devlaunchinjector.Main" || true; }
# A photograph of the whole virtual screen, for a person to look at (the fog, mostly): shot-<name>.png.
shot() { DISPLAY=:99 import -window root "shot-$1.png" 2>/dev/null || echo "(no photograph: $1)"; }
wait_for() {   # wait_for <log> <pattern> <seconds>
	for _ in $(seq 1 $(($3 / 5))); do
		grep -q "$2" "$1" 2>/dev/null && return 0
		crashed "$1" && return 1
		grep -q "BUILD FAILED" "$1" 2>/dev/null && return 1
		sleep 5
	done
	return 1
}

mkdir -p run
echo "onboardAccessibility:false" > run/options.txt

echo "== 1. the first screen"
xvfb-run -n 99 -s "-screen 0 1280x720x24 -ac" $GRADLE runClient > client.log 2>&1 &
if ! wait_for client.log "the gate screen has been drawn" 900; then stop_game; echo "the gate screen was never drawn"; exit 1; fi
sleep 30
shot gate
stop_game
if crashed client.log; then echo "crashed on the first screen"; exit 1; fi

echo "== 2. a world"
echo "eula=true" > run/eula.txt
cat > run/server.properties <<PROPS
online-mode=false
enable-rcon=true
rcon.password=smoke
rcon.port=25575
spawn-protection=0
PROPS
$GRADLE runServer > server.log 2>&1 &
if ! wait_for server.log "Done (" 900; then stop_game; echo "the server never finished starting"; exit 1; fi
python3 - <<'PY'
import socket, struct
s = socket.create_connection(("127.0.0.1", 25575), timeout=30)
def send(i, kind, body):
    data = struct.pack("<ii", i, kind) + body.encode() + b"\0\0"
    s.sendall(struct.pack("<i", len(data)) + data)
    size = struct.unpack("<i", s.recv(4))[0]
    s.recv(size)
send(1, 3, "smoke")
send(2, 2, "stop")
PY
for _ in $(seq 1 60); do pgrep -f "net.fabricmc.devlaunchinjector.Main" > /dev/null || break; sleep 2; done
stop_game
if [ ! -f run/world/level.dat ]; then echo "the server left no world behind"; exit 1; fi
mkdir -p run/saves
rm -rf run/saves/smoke
mv run/world run/saves/smoke

JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Doccupant.smoke=true" \
	xvfb-run -n 99 -s "-screen 0 1280x720x24 -ac" $GRADLE runClient --args="--quickPlaySingleplayer smoke" > world.log 2>&1 &
if ! wait_for world.log "the Occupant has been drawn" 1200; then stop_game; echo "the Occupant was never drawn"; exit 1; fi
sleep 20
shot world
sleep 10
stop_game
if crashed world.log; then echo "crashed in the world"; exit 1; fi
echo "== the client started, opened a world and drew the Occupant"
