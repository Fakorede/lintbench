package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "endsWith") return

                val receiverType = node.receiver?.getExpressionType() ?: return
                if (!context.evaluator.typeMatches(receiverType, "java.io.File")) return

                val arg = node.valueArguments.firstOrNull() ?: return
                val suffix = ConstantEvaluator.evaluate(context, arg) as? String ?: return
                if (!suffix.startsWith(".")) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "File.endsWith checks whole path components, not string suffixes. " +
                        "Consider using `file.path.endsWith(\"$suffix\")` or `file.extension == \"${suffix.substring(1)}\"` instead."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path
                components, not just string suffixes. This means that
                `File("foo.txt").endsWith(".txt")` returns false. Use
                `file.path.endsWith(suffix)` or `file.extension` when checking file
                extensions.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}