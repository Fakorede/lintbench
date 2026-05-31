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
# Java/Kotlin require file name to match public class name — extract it from content
if [[ "$EXT" == "kt" ]]; then
    DETECTOR_CLASS=$(grep -m1 'class ' "$GENERATED_FILE" | sed 's/.*class \([A-Za-z_][A-Za-z0-9_]*\).*/\1/')
else
    DETECTOR_CLASS=$(grep -m1 'class ' "$GENERATED_FILE" | sed 's/.*class \([A-Za-z_][A-Za-z0-9_]*\).*/\1/')
fi
if [[ -z "$DETECTOR_CLASS" ]]; then
    DETECTOR_CLASS="detector"
fi
cp "$GENERATED_FILE" "${DEST_DIR}/${DETECTOR_CLASS}.${EXT}"

# Inject stub @JvmField Issue declarations for any fields that
# BuiltinIssueRegistry (pre-compiled in lint-checks.jar) references but
# the generated file does not define. Without this, multi-issue detectors
# cause NoSuchFieldError -> ExceptionInInitializerError -> NoClassDefFoundError
# at test setup, masking the actual detector logic under evaluation.
python3 /eval/inject_stubs.py \
    "${DEST_DIR}/${DETECTOR_CLASS}.${EXT}" \
    "${DETECTOR_CLASS}" || true

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
# Strip TestLintTask API calls absent from lint-tests:31.7.0 (added in later AOSP snapshots).
# Inject sdkHome() so lint's UAST type-resolver picks up android.jar from the installed
# platform JARs, resolving android.* imports in inline test snippets.
# Inject allowMissingSdk() so tests don't fail if SDK structure check triggers first.
# Kotlin omits "new"; Java requires it — branch on extension.
if [[ "$TEST_EXT" == "kt" ]]; then
    sed -i \
        -e 's/\.verifyFixedFileSyntax([^)]*)//g' \
        -e 's/\.allowManifestMergerErrors([^)]*)//g' \
        -e 's|\.run()|.sdkHome(java.io.File("/opt/android-sdk")).allowMissingSdk().run()|g' \
        "${TEST_DEST_DIR}/${TEST_CLASS_SIMPLE}.${TEST_EXT}"
else
    sed -i \
        -e 's/\.verifyFixedFileSyntax([^)]*)//g' \
        -e 's/\.allowManifestMergerErrors([^)]*)//g' \
        -e 's|\.run()|.sdkHome(new java.io.File("/opt/android-sdk")).allowMissingSdk().run()|g' \
        "${TEST_DEST_DIR}/${TEST_CLASS_SIMPLE}.${TEST_EXT}"
fi

# Inject conditional stubs that are needed only for specific test files.
# GradleDetectorTestStub provides GradleDetectorTest.Companion.createRelativePaths,
# which ManifestDetectorTest imports. It is NOT injected when the real
# GradleDetectorTest.kt is the test file (that class defines it already).
if grep -q "GradleDetectorTest" "${TEST_DEST_DIR}/${TEST_CLASS_SIMPLE}.${TEST_EXT}" 2>/dev/null && \
   [[ "$TEST_CLASS_SIMPLE" != "GradleDetectorTest" ]]; then
    cp /eval/templates/GradleDetectorTestStub.kt \
       /eval/src/instance/kotlin/com/android/tools/lint/checks/GradleDetectorTestStub.kt
fi

# ---------------------------------------------------------------------------
# 3. Attempt compilation
# ---------------------------------------------------------------------------
COMPILE_LOG="/output/compile.log"
mkdir -p /output

# Remove cached class output for the instance-specific source sets so Gradle
# always recompiles the injected files. The warm-up baked in the image ran
# with empty src/generated/ and src/instance/ directories; Gradle's UP-TO-DATE
# check does not detect files added to those directories after the warm-up
# because it compares against the last-known output, which had no classes from
# those sources. Deleting the stale output forces a clean incremental compile.
rm -rf /eval/build/classes/kotlin/main \
       /eval/build/classes/java/main \
       /eval/build/classes/kotlin/test \
       /eval/build/classes/java/test \
       /eval/build/tmp/compileKotlin \
       /eval/build/tmp/compileJava \
       /eval/build/tmp/compileTestKotlin \
       /eval/build/tmp/compileTestJava

set +e
gradle compileKotlin compileJava compileTestKotlin compileTestJava \
    --no-daemon --offline -q \
    > "$COMPILE_LOG" 2>&1
COMPILE_EXIT=$?
set -e

if [[ $COMPILE_EXIT -ne 0 ]]; then
    # Extract meaningful error lines (filter Gradle noise)
    # Kotlin uses "e: file://..." prefix; Java uses "error:"; both are caught here.
    ERRORS=$({ grep -iE "^e: |error:|[Uu]nresolved reference|cannot access|does not contain|symbol not found" "$COMPILE_LOG" || true; } \
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

# Filter at class level only — JUnit 3 tests (which extend TestCase transitively
# through LintDetectorTest) do not support method-level --tests filtering in
# Gradle. Filtering to the class runs all methods; the XML parser below then
# selects only the methods listed in tests_to_run.
set +e
gradle test \
    --no-daemon --offline --info \
    --tests "${TEST_CLASS}" \
    > "$TEST_LOG" 2>&1
TEST_EXIT=$?
set -e

# ---------------------------------------------------------------------------
# 5. Parse XML test results
# ---------------------------------------------------------------------------
python3 - << PYEOF
import os, json, glob
import xml.etree.ElementTree as ET

xml_dir = "${XML_DIR}"
test_class = "${TEST_CLASS}"
methods_requested = [m.strip() for m in "${TEST_METHODS}".split(",") if m.strip()]
test_log = open("${TEST_LOG}").read()

tests_passed = []
tests_failed = []
failure_output_parts = []

# Parse JUnit XML output using ElementTree (handles both self-closing and
# non-self-closing <testcase> elements, which appear for passes and failures).
xml_files = glob.glob(os.path.join(xml_dir, "*.xml"))
for xml_file in xml_files:
    try:
        tree = ET.parse(xml_file)
        root = tree.getroot()
    except ET.ParseError:
        continue
    for tc in root.iter("testcase"):
        name = tc.get("name", "")
        if name not in methods_requested:
            continue
        failure = tc.find("failure")
        error   = tc.find("error")
        if failure is not None or error is not None:
            tests_failed.append(name)
            node = failure if failure is not None else error
            msg = (node.text or node.get("message", ""))[:300].strip()
            if msg:
                failure_output_parts.append(f"{name}: {msg}")
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
