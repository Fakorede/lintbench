package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

class StorageDetector : Detector(), Detector.SourceCodeScanner {

    companion object {
        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val JAVA_IO_FILE = "java.io.File"

        private val IMPLEMENTATION = Implementation(
            StorageDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        private val EXPLANATION = """
            When deciding if the device has enough disk space to hold your new data, call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since the former will consider any cached data that the system is willing to clear on your behalf.

            When you need to allocate disk space for large files, consider using the new `allocateBytes(FileDescriptor, long)` API, which will automatically clear cached files belonging to other apps (as needed) to meet your request.

            Note that these methods require API level 26. If your app is running on older devices, you will probably need to use both APIs, conditionally switching on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to see if you are already using both APIs, so if it warns even though you are already using the new API, consider moving the calls to the same file or suppressing the warning.
        """.trimIndent()

        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = EXPLANATION,
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(GET_USABLE_SPACE)

    override fun getApplicableCallNames(): List<String>? = listOf(GET_USABLE_SPACE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != JAVA_IO_FILE) {
            return
        }
        if (usesNewStorageApi(context)) {
            return
        }
        reportIssue(context, node)
    }

    override fun visitCallExpression(
        context: JavaContext,
        node: UCallExpression,
    ) {
        // Resolved calls are handled by visitMethodCall.
        if (node.resolve() != null) return
        if (node.methodName != GET_USABLE_SPACE) return
        if (usesNewStorageApi(context)) return
        reportIssue(context, node)
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using getUsableSpace() instead of getAllocatableBytes(UUID)",
        )
    }

    private fun usesNewStorageApi(context: JavaContext): Boolean {
        val uFile = context.uastFile ?: return false
        return uFile.allDescendants().any {
            it is UCallExpression && it.methodName == GET_ALLOCATABLE_BYTES
        }
    }

    private fun UElement.allDescendants(): Sequence<UElement> =
        sequenceOf(this) + uastChildren.asSequence().flatMap { it.allDescendants() }
}