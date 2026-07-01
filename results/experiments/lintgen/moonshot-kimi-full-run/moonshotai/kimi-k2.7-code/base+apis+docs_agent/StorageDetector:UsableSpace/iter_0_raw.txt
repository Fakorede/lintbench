package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class StorageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getUsableSpace")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Consider using `getAllocatableBytes(UUID)` instead of `getUsableSpace()` when checking available disk space; it accounts for cache the system can clear on your behalf."
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using `getUsableSpace()`",
            explanation = """
                When deciding if the device has enough disk space to hold your new data, call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since the former will consider any cached data that the system is willing to clear on your behalf.

                When you need to allocate disk space for large files, consider using the new `allocateBytes(FileDescriptor, long)` API, which will automatically clear cached files belonging to other apps as needed to meet your request.

                These methods require API level 26. On older devices you may need to use both APIs, conditionally switching on `Build.VERSION.SDK_INT`.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}