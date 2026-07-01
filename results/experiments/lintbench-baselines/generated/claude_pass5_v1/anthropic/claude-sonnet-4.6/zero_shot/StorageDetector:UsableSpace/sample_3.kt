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
        val ISSUE: Issue = Issue.create(
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
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                StorageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"
        private const val ALLOCATE_BYTES = "allocateBytes"

        private val NEW_STORAGE_METHODS = setOf(GET_ALLOCATABLE_BYTES, ALLOCATE_BYTES)
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(GET_USABLE_SPACE)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check if the method is getUsableSpace() on a File object
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != "java.io.File") {
            return
        }

        // Check if the same compilation unit also uses the new storage APIs
        if (usesNewStorageApi(context, node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            "Consider also using `getAllocatableBytes` to get the available bytes " +
                "the system is willing to allocate for you (it may be more than the free " +
                "space returned by `getUsableSpace`)"
        )
    }

    private fun usesNewStorageApi(context: JavaContext, node: UCallExpression): Boolean {
        val uFile = context.uastFile ?: return false
        val visitor = NewStorageApiVisitor()
        uFile.accept(visitor)
        return visitor.foundNewStorageApi
    }

    private class NewStorageApiVisitor : AbstractUastVisitor() {
        var foundNewStorageApi = false

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (foundNewStorageApi) {
                return true
            }
            val methodName = node.methodName
            if (methodName != null && NEW_STORAGE_METHODS.contains(methodName)) {
                foundNewStorageApi = true
                return true
            }
            return false
        }
    }
}