# LintBench evaluation build environment
#
# Provides a reproducible environment for compiling LLM-generated Android Lint
# detectors against the Lint API and running targeted test methods.
#
# Build:
#   docker build -t lintbench-eval .
#
# Run (called by run.sh):
#   docker run --rm \
#     -v /path/to/generated/detector.kt:/input/detector.kt:ro \
#     -v /path/to/lintbench/tests:/tests:ro \
#     lintbench-eval \
#     com.android.tools.lint.checks.AlarmDetectorTest \
#     testExactAlarmPermissions,testBasic

FROM eclipse-temurin:17-jdk-jammy

# ── System deps ──────────────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl unzip python3 \
    && rm -rf /var/lib/apt/lists/*

# ── Gradle ───────────────────────────────────────────────────────────────────
ENV GRADLE_VERSION=8.6
ENV GRADLE_HOME=/opt/gradle/gradle-${GRADLE_VERSION}
ENV PATH="${GRADLE_HOME}/bin:${PATH}"

RUN curl -fsSL \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt/gradle \
    && rm /tmp/gradle.zip

# ── Lint API version ─────────────────────────────────────────────────────────
# Pinned to match mirror-goog-studio-main at time of benchmark construction.
# Update this when re-running the corpus construction pipeline against a newer
# branch — the lint API version must match the source the detectors were
# written against.
ENV LINT_VERSION=31.7.0
ENV KOTLIN_VERSION=1.9.20

# ── Project skeleton ─────────────────────────────────────────────────────────
WORKDIR /eval

COPY settings.gradle.kts  .
COPY build.gradle.kts     .
COPY gradle.properties    .

# Pre-warm Gradle dependency cache (run during image build, not at eval time)
RUN gradle dependencies --no-daemon -q 2>&1 | tail -5 || true

# ── Test infrastructure ───────────────────────────────────────────────────────
# Copy the AbstractCheckTest and related test infrastructure from the AOSP
# lint-tests sources. These are compiled once into the image.
# The /tests volume is mounted read-only at runtime and contains the actual
# test files for each benchmark instance.
COPY src/ src/

# Pre-compile the test infrastructure
RUN gradle compileTestKotlin compileTestJava --no-daemon -q 2>&1 | tail -10 || true

# ── Entrypoint ───────────────────────────────────────────────────────────────
COPY run_inner.sh /usr/local/bin/run_inner.sh
RUN chmod +x /usr/local/bin/run_inner.sh

ENTRYPOINT ["/usr/local/bin/run_inner.sh"]
