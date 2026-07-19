#!/usr/bin/env bash
#
# Generates a deterministic vanilla ground truth world with the strict
# sequential single-chunk forceload ladder (MISSING.md item 2).
#
# Differences from gen_census.sh that make the result reproduce the modeled
# decoration order exactly:
#   - the server runs with -Dmax.bg.threads=1 (single chunk worker, so the
#     ladder's task order is deterministic),
#   - pause-when-empty-seconds=0 keeps the tick loop paused for the whole
#     run, so no scheduled fluid ticks ever fire and the only water spread
#     round is the synchronous FULL-promotion post-process pass,
#   - forceloads are single chunks in scanline order (x outer, z inner) and
#     are never removed, so no chunk unloads mid-ladder and structure
#     reference sets keep their original insertion-table order.
#
# Usage: scripts/gen_ladder.sh <worldDir> <seed> <radiusChunks> [cadenceSeconds] [bundlerJar]
#
# Forceloads cover chunks [-radius, radius) on both axes, decorating
# [-radius-1, radius] like the compare harness's modeled ladder expects.

set -euo pipefail

if [ "$#" -lt 3 ]; then
    echo "Usage: $0 <worldDir> <seed> <radiusChunks> [cadenceSeconds] [bundlerJar]" >&2
    exit 1
fi

world_dir="$1"
seed="$2"
radius="$3"
cadence="${4:-0.5}"
bundler_jar="$(realpath "${5:-data/mc/26.2/server-bundler.jar}")"

mkdir -p "$world_dir"
world_dir="$(realpath "$world_dir")"

echo "eula=true" > "$world_dir/eula.txt"

cat > "$world_dir/server.properties" <<EOF
accepts-transfers=false
allow-flight=false
broadcast-console-to-ops=true
broadcast-rcon-to-ops=true
difficulty=easy
enable-code-of-conduct=false
enable-jmx-monitoring=false
enable-query=false
enable-rcon=false
enable-status=true
enforce-secure-profile=true
enforce-whitelist=false
entity-broadcast-range-percentage=100
force-gamemode=false
function-permission-level=2
gamemode=creative
generate-structures=true
generator-settings={}
hardcore=false
hide-online-players=false
initial-disabled-packs=
initial-enabled-packs=vanilla
level-name=world
level-seed=$seed
level-type=minecraft\:normal
log-ips=true
max-chained-neighbor-updates=1000000
max-players=1
max-tick-time=-1
max-world-size=29999984
motd=worldgen-ladder
network-compression-threshold=256
online-mode=false
op-permission-level=4
pause-when-empty-seconds=0
player-idle-timeout=0
prevent-proxy-connections=false
query.port=25565
rate-limit=0
rcon.password=
rcon.port=25575
region-file-compression=deflate
require-resource-pack=false
resource-pack=
resource-pack-id=
resource-pack-prompt=
resource-pack-sha1=
server-ip=
server-port=38302
simulation-distance=4
spawn-protection=0
status-heartbeat-interval=0
sync-chunk-writes=false
use-native-transport=true
view-distance=4
white-list=false
EOF

cmd_pipe="$world_dir/cmd_pipe"
rm -f "$cmd_pipe"
mkfifo "$cmd_pipe"

log_file="$world_dir/server-out.log"

cd "$world_dir"
tail -f "$cmd_pipe" | java -Dmax.bg.threads=1 -jar "$bundler_jar" nogui > "$log_file" 2>&1 &
server_pid=$!

cleanup() {
    if kill -0 "$server_pid" 2>/dev/null; then
        kill "$server_pid" 2>/dev/null || true
    fi
    rm -f "$cmd_pipe"
}
trap cleanup EXIT

echo "Waiting for server to start (pid $server_pid, log $log_file)..."
for _ in $(seq 1 180); do
    if grep -q "Done" "$log_file" 2>/dev/null; then
        break
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        echo "Server process exited before starting up, see $log_file" >&2
        exit 1
    fi
    sleep 1
done
if ! grep -q "Done" "$log_file" 2>/dev/null; then
    echo "Server did not finish starting up in time, see $log_file" >&2
    exit 1
fi
echo "Server started, letting the pause engage..."
sleep 5

total=$((4 * radius * radius))
count=0
for chunk_x in $(seq $((-radius)) $((radius - 1))); do
    for chunk_z in $(seq $((-radius)) $((radius - 1))); do
        echo "forceload add $((chunk_x * 16)) $((chunk_z * 16))" > "$cmd_pipe"
        count=$((count + 1))
        sleep "$cadence"
    done
    echo "Column $chunk_x done ($count / $total chunks)..."
done

echo "Ladder complete, flushing..."
sleep 30
echo "save-all flush" > "$cmd_pipe"
sleep 10
echo "stop" > "$cmd_pipe"

for _ in $(seq 1 600); do
    if ! kill -0 "$server_pid" 2>/dev/null; then
        break
    fi
    sleep 1
done

if kill -0 "$server_pid" 2>/dev/null; then
    echo "Server did not stop in time, see $log_file" >&2
    exit 1
fi

trap - EXIT
rm -f "$cmd_pipe"
echo "Sequential-ladder world generated at $world_dir"
