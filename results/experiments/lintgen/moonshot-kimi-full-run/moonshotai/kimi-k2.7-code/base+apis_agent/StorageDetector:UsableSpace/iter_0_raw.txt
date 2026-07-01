package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
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

    private var hasAllocatableBytes = false

    override fun beforeCheckFile(context: Context) {
        super.beforeCheckFile(context)
        hasAllocatableBytes = false
        if (context is JavaContext) {
            hasAllocatableBytes = containsAllocatableBytes(context)
        }
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf(GET_USABLE_SPACE, GET_ALLOCATABLE_BYTES)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (node.methodName) {
            GET_ALLOCATABLE_BYTES -> {
                if (context.evaluator.isMemberInClass(method, "android.os.storage.StorageManager")) {
                    hasAllocatableBytes = true
                }
            }
            GET_USABLE_SPACE -> {
                if (hasAllocatableBytes) {
                    return
                }
                if (context.evaluator.isMemberInClass(method, "java.io.File")) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using `getUsableSpace()` should be avoided; " +
                                "use `StorageManager#getAllocatableBytes(UUID)` instead."
                    )
                }
            }
        }
    }

    private fun containsAllocatableBytes(context: JavaContext): Boolean {
        val file: UFile = context.uastFile ?: return false
        var found = false
        file.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == GET_ALLOCATABLE_BYTES) {
                    val resolved = node.resolve()
                    if (resolved != null &&
                        context.evaluator.isMemberInClass(
                            resolved,
                            "android.os.storage.StorageManager"
                        )
                    ) {
                        found = true
                    }
                }
                return false
            }
        })
        return found
    }

    companion object {
        private const val GET_USABLE_SPACE = "getUsableSpace"
        private const val GET_ALLOCATABLE_BYTES = "getAllocatableBytes"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using `getUsableSpace()` instead of `getAllocatableBytes(UUID)`",
            explanation = """
                When you need to allocate disk space for large files, consider using the new
                `allocateBytes(FileDescriptor, long)` API, which will automatically clear
                cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, call
                `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since the
                former will consider any cached data that the system is willing to clear on
                your behalf.

                Note that these methods require API level 26. If your app is running on older
                devices, you will probably need to use both APIs, conditionally switching on
                `Build.VERSION.SDK_INT`. This check only looks in the same compilation unit to
                see if you are already using `getAllocatableBytes(UUID)`, so if it warns even
                though you are already using the new API, consider moving the calls to the same
                file or suppressing the warning.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}