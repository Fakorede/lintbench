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

    override fun getApplicableMethodNames(): List<String> {
        return listOf("endsWith")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = node.receiverType ?: node.receiver?.getExpressionType()
        val isFile = receiverType != null && context.evaluator.typeMatches(receiverType, "java.io.File")

        if (!isFile) {
            val parameters = method.parameterList.parameters
            if (parameters.isEmpty() || !context.evaluator.typeMatches(parameters[0].type, "java.io.File")) {
                return
            }
        }

        val arguments = node.valueArguments
        if (arguments.size == 1) {
            val argument = arguments[0]
            val constant = ConstantEvaluator.evaluate(context, argument)
            if (constant is String) {
                if (constant.startsWith(".") && constant.length > 1) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using `File.endsWith` with a file extension suffix. This checks whole path components, not string suffixes. Use `file.path.endsWith(...)` or `file.extension` instead."
                    )
                }
            }
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