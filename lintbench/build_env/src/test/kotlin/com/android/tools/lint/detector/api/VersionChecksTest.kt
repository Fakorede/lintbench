package com.android.tools.lint.detector.api

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles

// Stubs for AOSP-internal test helpers not available in the public Maven artifact.
fun getNewAndroidOsBuildStub(): TestFile = TestFiles.kotlin("// stub android os build")

fun getRequiresExtensionStub(): TestFile = TestFiles.kotlin("// stub requires extension")
