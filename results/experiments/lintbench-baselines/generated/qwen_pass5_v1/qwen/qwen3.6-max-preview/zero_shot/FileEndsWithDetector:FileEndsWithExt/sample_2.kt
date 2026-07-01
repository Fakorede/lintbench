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
import org.jetbrains.uast.evaluateString

class FileEndsWithDetector : Detector(), Detector.UastScanner {

    override fun getApplicableMethodNames(): List<String>? = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiver = node.receiver ?: return
        val receiverType = receiver.getExpressionType() ?: return

        if (!context.evaluator.typeMatches(receiverType, "java.io.File")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 1) return

        val arg = args[0]
        val argValue = arg.evaluateString()
        if (argValue != null && argValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks path components, not string suffixes. Use `file.path.endsWith(...)` or `file.extension == ...` instead."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = "The Kotlin extension method `File.endsWith(suffix)` checks whole path components, " +
                    "not just string suffixes. This means that `File(\"foo.txt\").endsWith(\".txt\")` will return " +
                    "false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}