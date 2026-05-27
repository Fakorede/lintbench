# LintBench evaluation build environment
#
# Provides a reproducible environment for compiling developer and LLM-generated Android Lint
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
    curl unzip python3 git \
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

# ── Android platform JARs ────────────────────────────────────────────────────
# Provides android.jar for API levels 3–34 so lint tests can resolve
# android.* imports in their inline test snippets without needing a full SDK.
# Uses a sparse blob-filter clone so only the android.jar files are fetched.
ENV ANDROID_HOME=/opt/android-sdk

RUN mkdir -p ${ANDROID_HOME}/platforms && \
    git clone --filter=blob:none --no-checkout --depth=1 \
        https://github.com/sable/android-platforms.git /tmp/android-platforms && \
    cd /tmp/android-platforms && \
    git sparse-checkout init --cone && \
    git sparse-checkout set \
        android-3  android-4  android-5  android-6  android-7  android-8  android-9 \
        android-10 android-11 android-12 android-13 android-14 android-15 android-16 \
        android-17 android-18 android-19 android-21 android-22 android-23 android-24 \
        android-25 android-26 android-27 android-28 android-29 android-30 android-31 \
        android-32 android-33 android-34 && \
    git checkout HEAD && \
    cp -r android-* ${ANDROID_HOME}/platforms/ && \
    rm -rf /tmp/android-platforms && \
    for dir in ${ANDROID_HOME}/platforms/android-*; do \
        api=$(basename "$dir" | sed 's/android-//'); \
        printf "AndroidVersion.ApiLevel=%s\nPlatform.Version=%s\nPkg.Revision=1\n" "$api" "$api" \
            > "${dir}/source.properties"; \
    done

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

# Pre-warm Gradle dependency cache including the Kotlin compiler toolchain.
# A stub Kotlin file is needed to force kotlinCompilerClasspath resolution;
# without it, compileKotlin exits immediately (no sources) and skips the download.
RUN mkdir -p src/generated/kotlin/com/android/tools/lint/checks && \
    echo 'package com.android.tools.lint.checks' \
         > src/generated/kotlin/com/android/tools/lint/checks/Stub.kt && \
    gradle dependencies compileKotlin --no-daemon -q 2>&1 | tail -5 || true && \
    rm -f src/generated/kotlin/com/android/tools/lint/checks/Stub.kt

# ── Test infrastructure ───────────────────────────────────────────────────────
# Copy the AbstractCheckTest and related test infrastructure from the AOSP
# lint-tests sources. These are compiled once into the image.
# The /tests volume is mounted read-only at runtime and contains the actual
# test files for each benchmark instance.
COPY src/ src/

# Pre-compile the test infrastructure and warm the full test runtime classpath.
# Running `test` (even with failure) forces Gradle to resolve and cache all
# runtime-only transitive deps (ddmlib, groovy, asm-analysis, gson, etc.) so
# that offline mode works at eval time.
RUN gradle compileTestKotlin compileTestJava --no-daemon -q 2>&1 | tail -10 || true && \
    gradle test --no-daemon -q 2>&1 | tail -5 || true

# ── Conditional stubs ─────────────────────────────────────────────────────────
# Stubs that are only injected for specific test files (not pre-compiled).
COPY templates/ /eval/templates/

# ── Entrypoint ───────────────────────────────────────────────────────────────
COPY run_inner.sh /usr/local/bin/run_inner.sh
RUN chmod +x /usr/local/bin/run_inner.sh

ENTRYPOINT ["/usr/local/bin/run_inner.sh"]
