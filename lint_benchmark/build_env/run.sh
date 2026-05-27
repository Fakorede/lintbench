#!/usr/bin/env bash
# build_env/run.sh — Layer 2 entry point
#
# Called by 06_run_eval.py for each benchmark instance:
#
#   run.sh <generated_file> <test_class_fqn> <comma_separated_test_methods>
#
# Launches the Docker container with the generated detector and test file
# mounted, runs compilation and targeted test methods inside, and writes
# the JSON result to stdout.
#
# Prerequisites:
#   docker build -t lintbench-eval build_env/
#
# Environment variables (optional overrides):
#   LINTBENCH_IMAGE      Docker image name (default: lintbench-eval)
#   LINTBENCH_TESTS_DIR  Path to AOSP lint-tests source (for test files)
#   LINTBENCH_TIMEOUT    Per-instance timeout in seconds (default: 120)
#   LINTBENCH_MEMORY     Docker memory limit (default: 3g)
#   LINTBENCH_CPUS       Docker CPU limit (default: 2)
#   LINTBENCH_LOG_DIR    Directory to write per-instance logs (default: none)

set -euo pipefail

GENERATED_FILE="${1:-}"
TEST_CLASS_FQN="${2:-}"
TEST_METHODS="${3:-}"

if [[ -z "$GENERATED_FILE" || -z "$TEST_CLASS_FQN" || -z "$TEST_METHODS" ]]; then
    echo '{"compiled":false,"compile_errors":["run.sh: missing arguments"],"tests_run":[],"tests_passed":[],"tests_failed":[],"failure_output":""}' 
    exit 0
fi

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
IMAGE="${LINTBENCH_IMAGE:-lintbench-eval}"
MEMORY="${LINTBENCH_MEMORY:-3g}"
CPUS="${LINTBENCH_CPUS:-2}"
LOG_DIR="${LINTBENCH_LOG_DIR:-}"

# AOSP lint-tests source — needed to locate the test file
TESTS_SRC="${LINTBENCH_TESTS_DIR:-}"
if [[ -z "$TESTS_SRC" ]]; then
    # Default: build_env/ → lint_benchmark/ → lintbench/ → lint_codebase/base/
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    TESTS_SRC="${SCRIPT_DIR}/../../lint_codebase/base/lint/libs/lint-tests/src/test/java/com/android/tools/lint/checks"
fi

# ---------------------------------------------------------------------------
# Locate files
# ---------------------------------------------------------------------------
if [[ ! -f "$GENERATED_FILE" ]]; then
    echo "{\"compiled\":false,\"compile_errors\":[\"Generated file not found: ${GENERATED_FILE}\"],\"tests_run\":[],\"tests_passed\":[],\"tests_failed\":[],\"failure_output\":\"\"}"
    exit 0
fi

# Derive test filename from class FQN
# e.g.  com.android.tools.lint.checks.AlarmDetectorTest → AlarmDetectorTest
TEST_CLASS_SIMPLE="${TEST_CLASS_FQN##*.}"

# Find test file (try .kt then .java)
TEST_FILE=""
for ext in kt java; do
    candidate="${TESTS_SRC}/${TEST_CLASS_SIMPLE}.${ext}"
    if [[ -f "$candidate" ]]; then
        TEST_FILE="$candidate"
        TEST_EXT="$ext"
        break
    fi
done

if [[ -z "$TEST_FILE" ]]; then
    echo "{\"compiled\":false,\"compile_errors\":[\"Test file not found: ${TEST_CLASS_SIMPLE}.kt/.java in ${TESTS_SRC}\"],\"tests_run\":[],\"tests_passed\":[],\"tests_failed\":[],\"failure_output\":\"\"}"
    exit 0
fi

# Derive extension of generated file
GENERATED_EXT="${GENERATED_FILE##*.}"

# ---------------------------------------------------------------------------
# Create a temporary input directory with normalised filenames
# Docker bind mounts are simpler with predictable names
# ---------------------------------------------------------------------------
TMP_INPUT="$(mktemp -d /tmp/lintbench-in.XXXXXX)"
TMP_OUTPUT="$(mktemp -d /tmp/lintbench-out.XXXXXX)"
trap "rm -rf ${TMP_INPUT} ${TMP_OUTPUT}" EXIT

cp "$GENERATED_FILE"  "${TMP_INPUT}/detector.${GENERATED_EXT}"
cp "$TEST_FILE"       "${TMP_INPUT}/test.${TEST_EXT}"

# Per-instance log directory (optional)
INSTANCE_LABEL="$(basename "$(dirname "$GENERATED_FILE")")"
if [[ -n "$LOG_DIR" ]]; then
    INST_LOG_DIR="${LOG_DIR}/${INSTANCE_LABEL}"
    mkdir -p "$INST_LOG_DIR"
else
    INST_LOG_DIR=""
fi

# ---------------------------------------------------------------------------
# Run the Docker container
# ---------------------------------------------------------------------------
echo "[docker] starting container: ${INSTANCE_LABEL}" >&2

docker run \
    --rm \
    --memory="${MEMORY}" \
    --cpus="${CPUS}" \
    --network=none \
    -v "${TMP_INPUT}:/input:ro" \
    -v "${TMP_OUTPUT}:/output" \
    "${IMAGE}" \
    "${TEST_CLASS_FQN}" \
    "${TEST_METHODS}" \
2>"${TMP_OUTPUT}/docker_stderr.txt" || {
    EXIT=$?
    STDERR_SNIPPET="$(head -20 "${TMP_OUTPUT}/docker_stderr.txt" 2>/dev/null || true)"
    echo "[docker] container exited with code ${EXIT}: ${INSTANCE_LABEL}" >&2
    [[ -n "$INST_LOG_DIR" ]] && cp -r "${TMP_OUTPUT}/." "${INST_LOG_DIR}/" 2>/dev/null || true
    echo "{\"compiled\":false,\"compile_errors\":[\"Docker exited with code ${EXIT}\"],\"tests_run\":[],\"tests_passed\":[],\"tests_failed\":[],\"failure_output\":\"${STDERR_SNIPPET}\"}"
    exit 0
}

echo "[docker] container finished: ${INSTANCE_LABEL}" >&2

# Copy logs out of the container's output mount if log dir is set
if [[ -n "$INST_LOG_DIR" ]]; then
    cp -r "${TMP_OUTPUT}/." "${INST_LOG_DIR}/" 2>/dev/null || true
fi
