package com.android.tools.lint.checks

import java.io.File

/**
 * Stub providing cross-test helpers from GradleDetectorTest.
 * ManifestDetectorTest imports GradleDetectorTest.Companion.createRelativePaths.
 *
 * This file is excluded when the real GradleDetectorTest.kt is injected
 * (run_inner.sh removes it from the compiled source tree in that case).
 */
class GradleDetectorTest {
    companion object {
        fun createRelativePaths(sdkDir: File, paths: Array<String>) {
            for (path in paths) {
                val file = File(sdkDir, path.replace('/', File.separatorChar))
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                if (!file.exists()) {
                    file.createNewFile()
                }
            }
        }
    }
}
