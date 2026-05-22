import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") version "1.9.20"
    java
}

// Lint API version — keep in sync with Dockerfile ENV and gradle.properties
val lintVersion: String by project
val kotlinVersion: String by project

repositories {
    // Google Maven hosts the Android Lint artifacts
    google()
    mavenCentral()
}

dependencies {
    // ── Lint API (compile against) ────────────────────────────────────────
    implementation("com.android.tools.lint:lint-api:$lintVersion")
    implementation("com.android.tools.lint:lint-checks:$lintVersion")

    // ── Kotlin stdlib ─────────────────────────────────────────────────────
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")

    // ── Test infrastructure ───────────────────────────────────────────────
    // lint-tests provides AbstractCheckTest, TestLintTask, LintDetectorTest etc.
    testImplementation("com.android.tools.lint:lint-tests:$lintVersion")
    testImplementation("junit:junit:4.13.2")

    // lint provides LintCliClient (needed for TestLintClient hierarchy resolution)
    testImplementation("com.android.tools.lint:lint:$lintVersion")

    // Tools artifacts: promote to testImplementation so test files can import
    // SdkVersionInfo, FileUtils, Version, FontProviderKt, GoogleMavenRepository etc.
    // at compile time. These jars were already cached as testRuntimeOnly.
    testImplementation("com.android.tools:sdklib:$lintVersion")
    testImplementation("com.android.tools:common:$lintVersion")
    testImplementation("com.android.tools:sdk-common:$lintVersion")

    // Mockito — used by GradleDetectorTest and AppLinksValidDetectorTest
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")

    // Runtime-only deps pulled in transitively by lint-tests
    testRuntimeOnly("com.android.tools.external.com-intellij:intellij-core:$lintVersion")
    testRuntimeOnly("com.android.tools.external.com-intellij:kotlin-compiler:$lintVersion")
    testRuntimeOnly("com.android.tools.external.org-jetbrains:uast:$lintVersion")
    testRuntimeOnly("com.android.tools.layoutlib:layoutlib-api:$lintVersion")
    testRuntimeOnly("net.sf.kxml:kxml2:2.3.0")
    testRuntimeOnly("org.codehaus.groovy:groovy:3.0.21")
    testRuntimeOnly("org.ow2.asm:asm:9.6")
    testRuntimeOnly("org.ow2.asm:asm-tree:9.6")
}

// ── Source sets ───────────────────────────────────────────────────────────────
// The generated detector file and the test file are injected at runtime by
// run_inner.sh into the appropriate source directories before compilation.
sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin", "src/generated/kotlin")
        }
        java {
            srcDirs("src/main/java", "src/generated/java")
        }
    }
    test {
        kotlin {
            srcDirs("src/test/kotlin", "src/instance/kotlin")
        }
        java {
            srcDirs("src/test/java", "src/instance/java")
        }
    }
}

// ── Compilation ───────────────────────────────────────────────────────────────
kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        // Suppress warnings about experimental APIs used in Lint internals
        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}

// ── Test task ─────────────────────────────────────────────────────────────────
tasks.test {
    useJUnit()

    // Tests to run are passed via system property from run_inner.sh
    // e.g. -Dlintbench.test.methods=testBasic,testExactAlarmPermissions
    val methodsProp = System.getProperty("lintbench.test.methods", "")
    if (methodsProp.isNotBlank()) {
        val methods = methodsProp.split(",").map { it.trim() }.filter { it.isNotBlank() }
        // JUnit test filtering: ClassName#methodName
        val testClass = System.getProperty("lintbench.test.class", "")
        if (testClass.isNotBlank() && methods.isNotEmpty()) {
            filter {
                methods.forEach { method ->
                    includeTestsMatching("$testClass#$method")
                }
            }
        }
    }

    // Capture stdout/stderr for failure analysis
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        showStandardStreams = true
        showExceptions = true
        showCauses = true
        showStackTraces = false  // keep output concise
    }

    // Write XML results for parsing by run_inner.sh
    reports {
        junitXml.required.set(true)
        html.required.set(false)
    }

    // Increase heap for large detectors (ApiDetector etc.)
    maxHeapSize = "1g"

    // Don't fail the Gradle build on test failure — run_inner.sh reads exit code
    ignoreFailures = true
}
