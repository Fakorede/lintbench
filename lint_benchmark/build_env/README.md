# LintBench Build Environment

Compiles a model-generated Android Lint detector against the pinned Lint API
and runs targeted test methods via JUnit. Called per-instance by `run_eval.py`.

---

## Files

```
build_env/
  Dockerfile          Docker image definition
  build.gradle.kts    Gradle project — Lint API deps + source sets + test config
  settings.gradle.kts Gradle settings
  gradle.properties   Pinned versions (lintVersion=31.7.0, kotlinVersion=1.9.20)
  run.sh              Outer script — called by run_eval.py on the host
  run_inner.sh        Inner script — runs inside the container, compiles + tests
  src/
    generated/        Detector file injected here at runtime by run_inner.sh
    instance/         Test file injected here at runtime by run_inner.sh
    main/             Shared infrastructure (empty for now)
    test/
      java/com/android/tools/lint/checks/
        AbstractCheckTest.java   copied from AOSP lint-tests
```

---

## Setup

### 1. Copy AbstractCheckTest from AOSP

```bash
# Run from lint_benchmark/ root
cp ../lint_codebase/base/lint/libs/lint-tests/src/test/java/com/android/tools/lint/checks/AbstractCheckTest.java \
   build_env/src/test/java/com/android/tools/lint/checks/
```

### 2. Build the Docker image

```bash
# Run from lint_benchmark/ root
docker build -t lintbench-eval build_env/
```

Pre-warms the Gradle dependency cache inside the image (~800MB, ~5 min on first build).

### 3. Smoke test

```bash
# Run from lint_benchmark/ root
AOSP=../lint_codebase/base/lint/libs
docker run --rm \
  -v "$(pwd)/${AOSP}/lint-checks/src/main/java/com/android/tools/lint/checks/AddJavascriptInterfaceDetector.kt":/input/detector.kt:ro \
  -v "$(pwd)/${AOSP}/lint-tests/src/test/java/com/android/tools/lint/checks/AddJavascriptInterfaceDetectorTest.kt":/input/test.kt:ro \
  lintbench-eval \
  com.android.tools.lint.checks.AddJavascriptInterfaceDetectorTest \
  test,testNoWarningWhenMinSdkAt17
```

Expected output:
```json
{
  "compiled": true,
  "compile_errors": [],
  "tests_run": ["test", "testNoWarningWhenMinSdkAt17"],
  "tests_passed": ["test", "testNoWarningWhenMinSdkAt17"],
  "tests_failed": [],
  "failure_output": ""
}
```

---

## Usage

```bash
# Run from lint_benchmark/ root
python run_eval.py \
  --benchmark   data/lintbench.json \
  --generated   generated/gpt-4o/zero_shot/ \
  --model       gpt-4o \
  --prompt      zero_shot \
  --out         results/gpt4o_zero_shot.json \
  --build-env   build_env/run.sh \
  --samples     5
```

---

## Architecture

```
run_eval.py                       (orchestrator — iterates instances)
    │
    │  subprocess per instance:
    │  build_env/run.sh <detector_file> <test_class_fqn> <test_methods>
    ▼
build_env/run.sh                  (host — copies files, launches container)
    │
    ▼
docker run lintbench-eval         (isolated container per instance)
    │
    ▼
build_env/run_inner.sh            (inside container)
    │  places detector → src/generated/
    │  places test file → src/instance/
    │  gradle compileKotlin compileJava compileTestKotlin compileTestJava
    │    → on failure: emit JSON with compile_errors, exit
    │  gradle test --tests <class>#<method>,...
    │  parse JUnit XML → emit JSON result to stdout
    ▼
run_eval.py reads JSON, aggregates pass@k metrics
```

---

## Environment variables

| Variable             | Default        | Description                          |
|----------------------|----------------|--------------------------------------|
| `LINTBENCH_IMAGE`    | `lintbench-eval` | Docker image name                  |
| `LINTBENCH_TESTS_DIR`| (auto)         | Path to AOSP lint-tests checks dir   |
| `LINTBENCH_TIMEOUT`  | `120`          | Per-instance timeout in seconds      |
| `LINTBENCH_MEMORY`   | `3g`           | Docker memory limit                  |
| `LINTBENCH_CPUS`     | `2`            | Docker CPU limit                     |

---

## Pinned versions

| Component | Version | Reason |
|-----------|---------|--------|
| Lint API  | 31.7.0  | Matches `mirror-goog-studio-main` at corpus construction time |
| Kotlin    | 1.9.20  | Dependency of lint-tests 31.7.0 |
| JUnit     | 4.13.2  | Dependency of lint-tests 31.7.0 |
| Java      | 17      | Minimum required by Lint 31.x |
| Gradle    | 8.6     | Compatible with JDK 17 + Kotlin 1.9 |

To update the Lint API version after re-running curation against a newer AOSP branch:
1. Update `lintVersion` in `gradle.properties`
2. Update `LINT_VERSION` in `Dockerfile`
3. Rebuild: `docker build -t lintbench-eval build_env/`

---

## Resource usage

Each container run uses up to 2 CPU cores and 3GB RAM. Gradle cold start adds
~20s overhead; test execution ranges from 10–90s depending on the detector.

Estimated wall time for the full benchmark at pass@1:
- Sequential: 439 instances × ~45s ≈ 5.5 hours
- Parallel (8 containers): ~45 minutes

Containers are fully isolated (Gradle cache is baked into the image), so
parallelisation is safe with no shared state.
