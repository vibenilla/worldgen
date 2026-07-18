#!/usr/bin/env bash
#
# Generates the vanilla ground truth world used by the structure census.
#
# Boots a vanilla server in a fresh directory, forceloads the chunk windows
# from a StructureCensus locate-phase plan file (one command per line, at
# most 256 chunks per forceload), then flushes and stops. The console is
# driven through a named pipe held open with `tail -f` so each forceload
# command can be sent as a separate write without the server's stdin closing
# between them.
#
# Usage: scripts/gen_census.sh <planFile> <worldDir> <seed> [bundlerJar]

set -euo pipefail

if [ "$#" -lt 3 ]; then
    echo "Usage: $0 <planFile> <worldDir> <seed> [bundlerJar]" >&2
    exit 1
fi

plan_file="$(realpath "$1")"
world_dir="$2"
seed="$3"
bundler_jar="$(realpath "${4:-data/mc/26.2/server-bundler.jar}")"

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
motd=worldgen-census
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
server-port=38301
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
tail -f "$cmd_pipe" | java -jar "$bundler_jar" nogui > "$log_file" 2>&1 &
server_pid=$!

cleanup() {
    if kill -0 "$server_pid" 2>/dev/null; then
        kill "$server_pid" 2>/dev/null || true
    fi
    rm -f "$cmd_pipe"
}
trap cleanup EXIT

echo "Waiting for server to start (pid $server_pid, log $log_file)..."
for _ in $(seq 1 120); do
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
echo "Server started."

command_count=0
while IFS= read -r line; do
    [ -z "$line" ] && continue
    echo "$line" > "$cmd_pipe"
    command_count=$((command_count + 1))
    if [ $((command_count % 20)) -eq 0 ]; then
        echo "Sent $command_count commands..."
        sleep 2
    else
        sleep 0.2
    fi
done < "$plan_file"

echo "Sent all $command_count commands from $plan_file, waiting for the server to stop..."
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
echo "Vanilla census world generated at $world_dir"
