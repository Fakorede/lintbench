# LintBench Build Environment

Compiles a model-generated (or real AOSP) Android Lint detector against the
pinned Lint API and runs targeted test methods via JUnit. Called per-instance
by `run_eval.py`, `oracle_eval.py`, and `stub_eval.py`.

---

## Files

```
build_env/
  Dockerfile            Docker image definition
  build.gradle.kts      Gradle project — Lint API deps, source sets, test config
  settings.gradle.kts   Gradle settings
  gradle.properties     Pinned versions (lintVersion=31.7.0, kotlinVersion=1.9.20)
  run.sh                Outer script — called on the host; copies files, launches container
  run_inner.sh          Inner script — runs inside the container; compiles and tests
  oracle_eval.py        Validates instances using real AOSP detectors as oracle
  stub_eval.py          Validates instances by testing against stubbed detectors
  src/
    generated/          Detector file injected at runtime by run_inner.sh
    instance/           Test file injected at runtime by run_inner.sh
    main/               Shared infrastructure (AbstractCheckTest, stubs)
    oracle/             Staging dir for oracle detector files (one subdir per instance_id)
    stub/               Staging dir for stub detector files (one subdir per instance_id)
    test/
      java/com/android/tools/lint/checks/
        AbstractCheckTest.java   copied from AOSP lint-tests
  templates/
    GradleDetectorTestStub.kt   Conditional stub injected for tests that import GradleDetectorTest
```

---

## Setup

### 1. Build the Docker image

```bash
# From repo root
docker build -t lintbench-eval lint_benchmark/build_env/
```

Pre-warms the Gradle dependency cache and Android platform JARs inside the
image (~800 MB, ~5–10 min on first build).

### 2. Build the stub generator

Required only for `stub_eval.py`. Needs a local JDK 17+ and Gradle (or uses
the wrapper).

```bash
cd lint_benchmark/stub_generator && ./gradlew shadowJar
```

Produces `lint_benchmark/stub_generator/build/libs/stub-generator.jar`.

---

## Dataset validation scripts

### oracle_eval.py

Runs the real AOSP detector source through the harness to identify which
instances are valid benchmark entries.

```bash
# From repo root — runs all instances, 4 parallel workers
python3 lint_benchmark/build_env/oracle_eval.py --workers 4 --timeout 180

# Filter by split or difficulty
python3 lint_benchmark/build_env/oracle_eval.py --split easy --workers 4

# Results → lint_benchmark/results/oracle/oracle_results.json
# Logs    → lint_benchmark/results/oracle/logs/<instance_id>/
# Passing instances written to lint_benchmark/data/dataset.jsonl
```

| Status        | Meaning |
|---------------|---------|
| `pass`        | Real detector compiles and all targeted tests pass → kept in dataset |
| `test_fail`   | Tests fail even with the real implementation (harness/stub gap) |
| `compile_fail`| Detector imports AOSP-internal APIs not available in the Maven artifact |

### stub_eval.py

Generates a minimal stub for each instance in `dataset.jsonl` — correct class
structure and `Issue` declarations preserved, all method bodies emptied — and
re-runs the tests. Tests must fail; a passing test indicates insufficient
discriminating power and the instance is dropped from `dataset.jsonl`.

```bash
# From repo root
python3 lint_benchmark/build_env/stub_eval.py --workers 4 --timeout 180

# Results → lint_benchmark/results/stub/stub_results.json
# Logs    → lint_benchmark/results/stub/logs/<instance_id>/
# dataset.jsonl updated in-place (stub-passing instances removed)
```

Stubs are pre-generated in a single JVM invocation via
`stub_generator/build/libs/stub-generator.jar` (Kotlin compiler PSI for `.kt`,
JavaParser for `.java`) then evaluated in parallel Docker containers.

---

## Architecture

```
oracle_eval.py / stub_eval.py / run_eval.py
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
    │  places detector → src/generated/<lang>/com/android/tools/lint/checks/
    │  places test     → src/instance/<lang>/com/android/tools/lint/checks/
    │  patches test: strips unavailable API calls, injects sdkHome() + allowMissingSdk()
    │  gradle compileKotlin compileJava compileTestKotlin compileTestJava
    │    → on failure: emit JSON {compiled:false, compile_errors:[…]}
    │  gradle test --tests <class>
    │  parse JUnit XML → emit JSON {compiled:true, tests_passed:[…], tests_failed:[…]}
    ▼
caller reads JSON stdout, aggregates results
```

---

## Environment variables

| Variable              | Default          | Description                        |
|-----------------------|------------------|------------------------------------|
| `LINTBENCH_IMAGE`     | `lintbench-eval` | Docker image name                  |
| `LINTBENCH_TESTS_DIR` | (auto)           | Path to AOSP lint-tests checks dir |
| `LINTBENCH_TIMEOUT`   | `120`            | Per-instance timeout in seconds    |
| `LINTBENCH_MEMORY`    | `3g`             | Docker memory limit                |
| `LINTBENCH_CPUS`      | `2`              | Docker CPU limit                   |
| `LINTBENCH_LOG_DIR`   | (none)           | Directory for per-instance logs    |

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
3. Rebuild: `docker build -t lintbench-eval lint_benchmark/build_env/`

---

## Resource usage

Each container uses up to 2 CPU cores and 3 GB RAM. Gradle cold start adds
~10–20s overhead; test execution ranges from 10–90s per instance.

Estimated wall time for the full 156-instance dataset at pass@1:

| Workers | Estimated time |
|---------|----------------|
| 1       | ~2 hours       |
| 4       | ~30 minutes    |
| 8       | ~15 minutes    |

Containers are fully isolated (Gradle cache baked into the image), so
parallelisation is safe with no shared state.
