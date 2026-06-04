plugins {
    kotlin("jvm") version "1.9.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin compiler PSI — for parsing .kt files
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.20")
    // JavaParser — for parsing .java files
    implementation("com.github.javaparser:javaparser-core:3.25.8")
}

application {
    mainClass.set("StubGeneratorKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.shadowJar {
    archiveBaseName.set("stub-generator")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
    // Strip signing metadata that causes JAR verification failures
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
}
