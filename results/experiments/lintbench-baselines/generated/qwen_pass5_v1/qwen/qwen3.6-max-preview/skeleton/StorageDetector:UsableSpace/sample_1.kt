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

    companion object {
        private val IMPLEMENTATION = Implementation(
            StorageDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UsableSpace",
            briefDescription = "Using getUsableSpace()",
            explanation = "When you need to allocate disk space for large files, consider using the new allocateBytes(FileDescriptor, long) API, which will automatically clear cached files belonging to other apps (as needed) to meet your request. When deciding if the device has enough disk space to hold your new data, call getAllocatableBytes(UUID) instead of using getUsableSpace(), since the former will consider any cached data that the system is willing to clear on your behalf. Note that these methods require API level 26. If your app is running on older devices, you will probably need to use both APIs, conditionally switching on Build.VERSION.SDK_INT.",
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getUsableSpace")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "java.io.File")) {
            return
        }
        if (usesNewStorageApi(context)) {
            return
        }
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Use `getAllocatableBytes(UUID)` instead of `getUsableSpace()`"
        )
    }

    private fun usesNewStorageApi(context: JavaContext): Boolean {
        val file = context.uastFile ?: return false
        var found = false
        file.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val name = node.methodName
                if (name == "allocateBytes" || name == "getAllocatableBytes") {
                    found = true
                    return true
                }
                return super.visitCallExpression(node)
            }
        })
        return found
    }
}