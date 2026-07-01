package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = node.receiverType
        val isFileReceiver = receiverType?.let { context.evaluator.isInstanceOf(it, "java.io.File", false) } == true

        val arg = if (isFileReceiver) {
            node.valueArguments.firstOrNull()
        } else {
            if (node.valueArguments.size == 2) {
                val firstArgType = node.valueArguments[0].getExpressionType()
                if (firstArgType != null && context.evaluator.isInstanceOf(firstArgType, "java.io.File", false)) {
                    node.valueArguments[1]
                } else {
                    null
                }
            } else {
                null
            }
        } ?: return

        val constantValue = ConstantEvaluator.evaluate(context, arg)
        if (constantValue is String && constantValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `File.endsWith` with a file extension suffix will return false. " +
                        "Use `file.path.endsWith` or `file.extension.equals` instead."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
            """.trimIndent(),
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