#!/usr/bin/env bash
# Usage:
#   chmod +x run_tests.sh
#   ./run_tests.sh

set -euo pipefail

# ====== CONFIG ======
JAVAC="javac"
JAVA="java"
SRC_DIR="src"
OUT_DIR="out"
LOG_DIR="logs"
CFG_FILE="network.config"
MAIN_CLASS_CANDIDATES=("council.CouncilMember" "council.paxos.CouncilMember")

# Scenario tuning (seconds / milliseconds)
WAIT_LISTEN_TIMEOUT=8      # seconds to wait for background listeners
S1_TIMEOUT=12              # seconds to wait for consensus in Scenario 1
S2_TIMEOUT=15              # seconds to wait for consensus in Scenario 2
S3_TIMEOUT=15              # seconds to wait for consensus in Scenario 3
S1_PROP_DELAY_MS=1200      # proposer delay for Scenario 1
S2_P1_DELAY_MS=1200        # proposer M1 delay for Scenario 2
S2_P8_DELAY_MS=1300        # proposer M8 delay for Scenario 2
S3_P4_DELAY_MS=1000        # proposer M4 delay for Scenario 3
S3_P2_DELAY_MS=6000        # proposer M2 delay for Scenario 3 (latent)
S3_P3_DELAY_MS=1200        # proposer M3 delay for Scenario 3 (will crash)

# ====== UTIL ======
banner() { echo -e "\n==================== $* ====================\n"; }

die() { echo "ERROR: $*" >&2; exit 1; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

ensure_prereqs() {
  need_cmd "$JAVAC"
  need_cmd "$JAVA"
  need_cmd "lsof"
  need_cmd "awk"
  [[ -f "$CFG_FILE" ]] || die "Missing $CFG_FILE. Place it next to this script."
}

# Compute cluster size and majority from network.config (non-empty, non-comment lines)
compute_majority() {
  local total
  total=$(grep -v '^\s*#' "$CFG_FILE" | grep -v '^\s*$' | wc -l | awk '{print $1}')
  if [[ "$total" -lt 1 ]]; then die "No members found in $CFG_FILE"; fi
  NODES="$total"
  MAJ=$(( total/2 + 1 ))
}

compile() {
  banner "Compile"
  rm -rf "$OUT_DIR"
  mkdir -p "$OUT_DIR"
  # Compile all Java sources into OUT_DIR
  find "$SRC_DIR" -name "*.java" | xargs "$JAVAC" -d "$OUT_DIR"
}

detect_main() {
  if [[ -f "$OUT_DIR/council/CouncilMember.class" ]]; then
    MAIN="${MAIN_CLASS_CANDIDATES[0]}"
  elif [[ -f "$OUT_DIR/council/paxos/CouncilMember.class" ]]; then
    MAIN="${MAIN_CLASS_CANDIDATES[1]}"
  else
    MAIN="${MAIN_CLASS_CANDIDATES[0]}"
  fi
  echo "Using MAIN=$MAIN  (nodes=$NODES, majority=$MAJ)"
}

# Kill previously running nodes (by main class), and free ports 9001..9009 if still bound.
kill_all() {
  pkill -f "$MAIN" >/dev/null 2>&1 || true
  for p in {9001..9009}; do
    if lsof -nP -iTCP:$p -sTCP:LISTEN >/dev/null 2>&1; then
      local pid
      pid=$(lsof -nP -iTCP:$p -sTCP:LISTEN | awk 'NR==2 {print $2}')
      [[ -n "${pid:-}" ]] && kill -9 "$pid" || true
    fi
  done
}

# Always cleanup at script end (success or failure)
cleanup() {
  kill_all
}
trap cleanup EXIT

fresh_logs() {
  rm -rf "$LOG_DIR"
  mkdir -p "$LOG_DIR"
}

# Start all nodes except the space-separated list passed as $1
start_background_nodes() {
  local skip_ids="${1:-}"
  for i in $(seq 1 "$NODES"); do
    local id="M$i"
    if [[ " $skip_ids " == *" $id "* ]]; then
      continue
    fi
    echo "Starting $id (background)"
    "$JAVA" -cp "$OUT_DIR" "$MAIN" "$id" </dev/null >"$LOG_DIR/$id.log" 2>&1 &
    sleep 0.05
  done
}

# Wait until K nodes have printed "listening on 900x" in their logs
wait_until_listening() {
  local expected="$1"  # number of listeners expected
  local timeout="$2"   # seconds
  local start
  start=$(date +%s)
  while true; do
    local now
    now=$(date +%s)
    (( now - start > timeout )) && return 1
    local count
    count=$(grep -h "listening on 900" "$LOG_DIR"/M*.log 2>/dev/null | wc -l | awk '{print $1}')
    if (( count >= expected )); then
      return 0
    fi
    sleep 0.2
  done
}

# Start a single proposer in foreground (via pipeline), writing output to its log.
# After delay_ms, its candidate is piped into stdin once.
start_proposer_with_input() {
  local proposer_id="$1"   # e.g., "M4"
  local candidate="$2"     # e.g., "M5"
  local delay_ms="$3"      # e.g., 1200
  echo "Starting proposer $proposer_id -> $candidate (delay ${delay_ms}ms)"
  ( { sleep "$(awk "BEGIN {print $delay_ms/1000}")"; echo "$candidate"; } \
    | "$JAVA" -cp "$OUT_DIR" "$MAIN" "$proposer_id" ) >"$LOG_DIR/$proposer_id.log" 2>&1 &
}

# Wait until at least MAJ nodes have printed a "CONSENSUS:" line, or time out.
await_majority_or_timeout() {
  local timeout_sec="$1"
  local start_ts
  start_ts=$(date +%s)
  while true; do
    local now
    now=$(date +%s)
    (( now - start_ts > timeout_sec )) && { echo "Timed out after ${timeout_sec}s"; return 1; }
    local count
    count=$(grep -h "CONSENSUS:" "$LOG_DIR"/M*.log 2>/dev/null | wc -l | awk '{print $1}')
    if (( count >= MAJ )); then
      echo "Observed CONSENSUS lines: $count (>= $MAJ)"
      return 0
    fi
    sleep 0.4
  done
}

print_summary() {
  echo "---- SUMMARY (last lines) ----"
  tail -n 3 "$LOG_DIR"/M*.log || true
  echo "------------------------------"
}

# ====== SCENARIOS ======

# Scenario 1: Ideal network — single proposal (M4 proposes M5)
scenario1() {
  banner "Scenario 1 - Ideal Network (single proposal)"
  kill_all
  fresh_logs
  start_background_nodes "M4"
  wait_until_listening $((NODES-1)) "$WAIT_LISTEN_TIMEOUT" || echo "WARN: not all background nodes are listening yet"
  start_proposer_with_input "M4" "M5" "$S1_PROP_DELAY_MS"
  if await_majority_or_timeout "$S1_TIMEOUT"; then
    echo "Scenario 1: PASS"
  else
    echo "Scenario 1: FAIL"
  fi
  print_summary
}

# Scenario 2: Concurrent proposals (M1 proposes M1, M8 proposes M8)
scenario2() {
  banner "Scenario 2 - Concurrent Proposals (conflict resolution)"
  kill_all
  fresh_logs
  start_background_nodes "M1 M8"
  wait_until_listening $((NODES-2)) "$WAIT_LISTEN_TIMEOUT" || echo "WARN: not all background nodes are listening yet"
  start_proposer_with_input "M1" "M1" "$S2_P1_DELAY_MS"
  start_proposer_with_input "M8" "M8" "$S2_P8_DELAY_MS"
  if await_majority_or_timeout "$S2_TIMEOUT"; then
    echo "Scenario 2: PASS"
  else
    echo "Scenario 2: FAIL"
  fi
  print_summary
}

# Scenario 3: Fault tolerance (M2 latent, M3 crashes, others standard)
scenario3() {
  banner "Scenario 3 - Fault Tolerance (latent/failure/standard mix)"
  kill_all
  fresh_logs
  start_background_nodes "M4 M2 M3"
  wait_until_listening $((NODES-3)) "$WAIT_LISTEN_TIMEOUT" || echo "WARN: not all background nodes are listening yet"

  # 3a: Standard proposer (M4 -> M5) should drive consensus
  start_proposer_with_input "M4" "M5" "$S3_P4_DELAY_MS"

  # 3b: Latent proposer (M2 -> M2) fires much later; by then consensus likely done
  start_proposer_with_input "M2" "M2" "$S3_P2_DELAY_MS"

  # 3c: Failing proposer (M3 -> M3) starts then crashes quickly
  start_proposer_with_input "M3" "M3" "$S3_P3_DELAY_MS"
  ( sleep 2.2; pkill -f "$MAIN M3" || true ) &

  if await_majority_or_timeout "$S3_TIMEOUT"; then
    echo "Scenario 3: PASS"
  else
    echo "Scenario 3: FAIL"
  fi
  print_summary
}

# ====== MAIN FLOW ======
ensure_prereqs
compute_majority
compile
detect_main

scenario1
scenario2
scenario3

banner "DONE."
