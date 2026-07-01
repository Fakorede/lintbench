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
import org.jetbrains.uast.visitor.AbstractUastVisitor

class StorageDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using `getUsableSpace()`",
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
            implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true
        )

        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val ALLOCATE_BYTES = "allocateBytes"
        private const val STORAGE_MANAGER = "android.os.storage.StorageManager"
    }

    /**
     * Whether the current file contains calls to the new storage allocation APIs.
     * This is set during file analysis.
     */
    private var fileContainsNewApi = false

    override fun getApplicableMethodNames(): List<String> {
        return listOf(GET_USABLE_SPACE, GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES)
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // This is called for all applicable method names; we use it to detect
        // presence of new API calls in the file for context checking.
        val methodName = node.methodName ?: return
        if (methodName == GET_ALLOCATABLE_BYTES || methodName == ALLOCATE_BYTES) {
            fileContainsNewApi = true
        }
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = node.methodName ?: return

        if (methodName == GET_USABLE_SPACE) {
            // Check if the containing file also uses the new APIs
            if (!fileContainsNewApi && !fileUsesNewStorageApi(context, node)) {
                val message = "Consider using `getAllocatableBytes(UUID)` instead of " +
                        "`getUsableSpace()` to take advantage of cached data that the system " +
                        "is willing to clear on your behalf"
                context.report(ISSUE, node, context.getLocation(node), message)
            }
        }
    }

    /**
     * Scans the entire file to check if any calls to the new storage APIs
     * (getAllocatableBytes or allocateBytes) exist.
     */
    private fun fileUsesNewStorageApi(context: JavaContext, node: UCallExpression): Boolean {
        val uFile = node.getContainingUFile() ?: return false
        val visitor = NewStorageApiVisitor(context)
        uFile.accept(visitor)
        return visitor.foundNewApi
    }

    private class NewStorageApiVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        var foundNewApi = false

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (foundNewApi) return false
            val methodName = node.methodName
            if (methodName == GET_ALLOCATABLE_BYTES || methodName == ALLOCATE_BYTES) {
                // Check if it's called on a StorageManager
                val resolved = node.resolve()
                if (resolved is PsiMethod) {
                    val containingClass = resolved.containingClass?.qualifiedName
                    if (containingClass == STORAGE_MANAGER) {
                        foundNewApi = true
                        return false
                    }
                }
                // Even if we can't resolve, treat presence of these method names as a signal
                foundNewApi = true
            }
            return false
        }
    }

    private fun UCallExpression.getContainingUFile(): UFile? {
        var parent = this.uastParent
        while (parent != null) {
            if (parent is UFile) return parent
            parent = parent.uastParent
        }
        return null
    }
}