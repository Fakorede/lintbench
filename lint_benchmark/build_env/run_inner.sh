#!/usr/bin/env bash
# run_inner.sh — runs INSIDE the Docker container
#
# Called by run.sh (the outer script) via docker run.
#
# Arguments:
#   $1  test_class_fqn       e.g. com.android.tools.lint.checks.AlarmDetectorTest
#   $2  test_methods          comma-separated, e.g. testBasic,testExactAlarmPermissions
#
# Mounts expected by docker run (set by run.sh):
#   /input/detector.<kt|java>   the generated detector file (read-only)
#   /input/test.<kt|java>       the test file from AOSP (read-only)
#
# Writes JSON to stdout:
#   {
#     "compiled":       bool,
#     "compile_errors": [str, ...],
#     "tests_run":      [str, ...],
#     "tests_passed":   [str, ...],
#     "tests_failed":   [str, ...],
#     "failure_output": str
#   }

set -euo pipefail

TEST_CLASS="${1:-}"
TEST_METHODS="${2:-}"

if [[ -z "$TEST_CLASS" || -z "$TEST_METHODS" ]]; then
    echo '{"compiled":false,"compile_errors":["Missing arguments: test_class test_methods"],"tests_run":[],"tests_passed":[],"tests_failed":[],"failure_output":""}' 
    exit 0
fi

# ---------------------------------------------------------------------------
# 1. Find and place the generated detector file
# ---------------------------------------------------------------------------
GENERATED_FILE=""
for ext in kt java; do
    if [[ -f "/input/detector.${ext}" ]]; then
        GENERATED_FILE="/input/detector.${ext}"
        EXT="${ext}"
        break
    fi
done

if [[ -z "$GENERATED_FILE" ]]; then
    echo '{"compiled":false,"compile_errors":["No detector file found at /input/detector.kt or /input/detector.java"],"tests_run":[],"tests_passed":[],"tests_failed":[],"failure_output":""}'
    exit 0
fi

# Place generated detector into the right source directory
if [[ "$EXT" == "kt" ]]; then
    DEST_DIR="/eval/src/generated/kotlin/com/android/tools/lint/checks"
else
    DEST_DIR="/eval/src/generated/java/com/android/tools/lint/checks"
fi
mkdir -p "$DEST_DIR"
cp "$GENERATED_FILE" "${DEST_DIR}/"

# ---------------------------------------------------------------------------
# 2. Find and place the test file
# ---------------------------------------------------------------------------
TEST_FILE=""
for ext in kt java; do
    if [[ -f "/input/test.${ext}" ]]; then
        TEST_FILE="/input/test.${ext}"
        TEST_EXT="${ext}"
        break
    fi
done

if [[ -z "$TEST_FILE" ]]; then
    echo '{"compiled":false,"compile_errors":["No test file found at /input/test.kt or /input/test.java"],"tests_run":[],"tests_passed":[],"tests_failed":[],"failure_output":""}'
    exit 0
fi

if [[ "$TEST_EXT" == "kt" ]]; then
    TEST_DEST_DIR="/eval/src/instance/kotlin/com/android/tools/lint/checks"
else
    TEST_DEST_DIR="/eval/src/instance/java/com/android/tools/lint/checks"
fi
mkdir -p "$TEST_DEST_DIR"
# Java requires public class Foo to live in Foo.java — use the class simple name
TEST_CLASS_SIMPLE="${TEST_CLASS##*.}"
cp "$TEST_FILE" "${TEST_DEST_DIR}/${TEST_CLASS_SIMPLE}.${TEST_EXT}"

# ---------------------------------------------------------------------------
# 3. Attempt compilation
# ---------------------------------------------------------------------------
COMPILE_LOG="/output/compile.log"
mkdir -p /output

set +e
gradle compileKotlin compileJava compileTestKotlin compileTestJava \
    --no-daemon --offline -q \
    > "$COMPILE_LOG" 2>&1
COMPILE_EXIT=$?
set -e

if [[ $COMPILE_EXIT -ne 0 ]]; then
    # Extract meaningful error lines (filter Gradle noise)
    ERRORS=$({ grep -E "error:|unresolved reference|cannot access|does not contain" "$COMPILE_LOG" || true; } \
        | head -20 \
        | python3 -c "
import sys, json
lines = [l.rstrip() for l in sys.stdin]
print(json.dumps(lines))
")
    RAW=$(head -40 "$COMPILE_LOG" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read()))")
    echo "{\"compiled\":false,\"compile_errors\":${ERRORS},\"tests_run\":[],\"tests_passed\":[],\"tests_failed\":[],\"failure_output\":${RAW}}"
    exit 0
fi

# ---------------------------------------------------------------------------
# 4. Run targeted test methods
# ---------------------------------------------------------------------------
TEST_LOG="/output/test.log"
XML_DIR="/eval/build/test-results/test"

# Convert comma-separated methods to array
IFS=',' read -ra METHODS <<< "$TEST_METHODS"

set +e
gradle test \
    --no-daemon --offline -q \
    --tests "${TEST_CLASS}" \
    -Dlintbench.test.class="${TEST_CLASS}" \
    -Dlintbench.test.methods="${TEST_METHODS}" \
    > "$TEST_LOG" 2>&1
TEST_EXIT=$?
set -e

# ---------------------------------------------------------------------------
# 5. Parse XML test results
# ---------------------------------------------------------------------------
python3 - << PYEOF
import os, json, re, glob

xml_dir = "${XML_DIR}"
test_class = "${TEST_CLASS}"
methods_requested = [m.strip() for m in "${TEST_METHODS}".split(",") if m.strip()]
test_log = open("${TEST_LOG}").read()

tests_passed = []
tests_failed = []
failure_output_parts = []

# Parse JUnit XML output
xml_files = glob.glob(os.path.join(xml_dir, "*.xml"))
for xml_file in xml_files:
    content = open(xml_file).read()
    # Find all testcase elements
    for tc in re.finditer(r'<testcase[^>]*name="([^"]+)"[^>]*>(.*?)</testcase>', content, re.DOTALL):
        name = tc.group(1)
        body = tc.group(2)
        if name not in methods_requested:
            continue
        if '<failure' in body or '<error' in body:
            tests_failed.append(name)
            # Extract failure message
            msg = re.search(r'<(?:failure|error)[^>]*>(.*?)</(?:failure|error)>', body, re.DOTALL)
            if msg:
                failure_output_parts.append(f"{name}: {msg.group(1)[:300].strip()}")
        else:
            tests_passed.append(name)

# Methods not appearing in XML were not run (filter or missing)
not_run = [m for m in methods_requested if m not in tests_passed and m not in tests_failed]
# Treat not-run as failed (conservative)
tests_failed.extend(not_run)
if not_run:
    failure_output_parts.append(f"Not executed: {', '.join(not_run)}")

failure_output = "\n".join(failure_output_parts)
if not failure_output and "${TEST_EXIT}" != "0":
    # Fall back to raw log snippet
    failure_output = test_log[-1500:]

result = {
    "compiled":       True,
    "compile_errors": [],
    "tests_run":      methods_requested,
    "tests_passed":   tests_passed,
    "tests_failed":   tests_failed,
    "failure_output": failure_output[:2000],
}
print(json.dumps(result))
PYEOF

# Copy JUnit XML results to /output for the host to inspect
if [[ -d "$XML_DIR" ]]; then
    cp -r "$XML_DIR" /output/test-results 2>/dev/null || true
fi
