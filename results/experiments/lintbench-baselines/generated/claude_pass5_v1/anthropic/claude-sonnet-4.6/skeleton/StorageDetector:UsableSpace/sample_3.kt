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
import org.jetbrains.uast.UFile
import org.jetbrains.uast.getParentOfType

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            StorageDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = """
                When you need to allocate disk space for large files, consider using the new \
                `allocateBytes(FileDescriptor, long)` API, which will automatically clear \
                cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, \
                call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since \
                the former will consider any cached data that the system is willing to \
                clear on your behalf.

                Note that these methods require API level 26. If your app is running on \
                older devices, you will probably need to use both APIs, conditionally switching \
                on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to \
                see if you are already using both APIs, so if it warns even though you are \
                already using the new API, consider moving the calls to the same file or \
                suppressing the warning.
            """,
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val ALLOCATE_BYTES = "allocateBytes"
    }

    /**
     * Tracks whether the current file contains calls to the new storage APIs
     * (getAllocatableBytes or allocateBytes).
     */
    private var hasNewStorageApi = false

    /**
     * Stores pending reports (node + context) for getUsableSpace calls,
     * to be reported only if we haven't seen the new API in the same file.
     */
    private val pendingReports = mutableListOf<Pair<JavaContext, UCallExpression>>()

    override fun getApplicableMethodNames(): List<String> = listOf(
        GET_USABLE_SPACE,
        GET_ALLOCATABLE_BYTES,
        ALLOCATE_BYTES,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name
        when (methodName) {
            GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES -> {
                hasNewStorageApi = true
            }
            GET_USABLE_SPACE -> {
                pendingReports.add(Pair(context, node))
            }
        }
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // Intentionally left empty; logic is handled in visitMethodCall
    }

    override fun afterCheckFile(context: JavaContext) {
        if (!hasNewStorageApi) {
            for ((ctx, node) in pendingReports) {
                ctx.report(
                    ISSUE,
                    node,
                    ctx.getLocation(node),
                    "Consider also using `getAllocatableBytes` and `allocateBytes` " +
                        "instead of `getUsableSpace`",
                )
            }
        }
        // Reset state for next file
        hasNewStorageApi = false
        pendingReports.clear()
    }
}