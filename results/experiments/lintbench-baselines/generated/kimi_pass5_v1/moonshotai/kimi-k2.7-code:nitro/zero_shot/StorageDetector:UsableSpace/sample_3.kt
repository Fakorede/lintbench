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
import org.jetbrains.uast.visitor.AbstractUastVisitor

class StorageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getUsableSpace")

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        referenced: PsiMethod?
    ) {
        if (referenced == null || !context.evaluator.isMemberInClass(referenced, "java.io.File")) {
            return
        }

        val uastFile = context.uastFile ?: return
        var hasAllocatableBytes = false
        uastFile.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "getAllocatableBytes") {
                    hasAllocatableBytes = true
                    return true
                }
                return super.visitCallExpression(node)
            }
        })

        if (hasAllocatableBytes) {
            return
        }

        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            "Using `getUsableSpace()`; consider using `getAllocatableBytes(UUID)` (or use both and switch on `Build.VERSION.SDK_INT`)"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = """
                When you need to allocate disk space for large files, consider using the new `allocateBytes(FileDescriptor, long)` API, which will automatically clear cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since the former will consider any cached data that the system is willing to clear on your behalf.

                Note that these methods require API level 26. If your app is running on older devices, you will probably need to use both APIs, conditionally switching on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to see if you are already using both APIs, so if it warns even though you are already using the new API, consider moving the calls to the same file or suppressing the warning.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}