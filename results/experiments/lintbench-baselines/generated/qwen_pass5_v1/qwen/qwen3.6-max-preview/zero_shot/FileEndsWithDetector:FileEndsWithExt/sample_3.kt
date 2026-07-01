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
import org.jetbrains.uast.evaluate

class FileEndsWithDetector : Detector(), Detector.UastScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiver = node.receiver ?: return
        val receiverType = context.evaluator.getType(receiver) ?: return
        if (!context.evaluator.isSubtypeOf(receiverType, "java.io.File")) {
            return
        }

        if (node.valueArguments.size != 1) return

        val arg = node.valueArguments.first()
        val argValue = arg.evaluate() as? String ?: return
        if (argValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `File.endsWith()` with a file extension checks path components, not string suffixes. Use `file.path.endsWith()` or `file.extension == ...` instead."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = "The Kotlin extension method `File.endsWith(suffix)` checks whole path components, not just string suffixes. This means that `File(\"foo.txt\").endsWith(\".txt\")` will return false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}