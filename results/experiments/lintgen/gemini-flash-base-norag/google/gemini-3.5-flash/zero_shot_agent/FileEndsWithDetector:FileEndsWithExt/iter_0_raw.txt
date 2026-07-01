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

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File.endsWith used to check file extension",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
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

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        val receiverType = node.receiverType
        val isFileReceiver = receiverType != null && evaluator.typeMatches(receiverType, "java.io.File")

        val argument = if (isFileReceiver) {
            node.valueArguments.firstOrNull()
        } else {
            if (node.valueArguments.size == 2) {
                val firstArgType = node.valueArguments[0].getExpressionType()
                if (firstArgType != null && evaluator.typeMatches(firstArgType, "java.io.File")) {
                    node.valueArguments[1]
                } else null
            } else null
        } ?: return

        val constantValue = argument.evaluate() as? String ?: return

        if (constantValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `File.endsWith` to match a file extension; this matches full path components, not string suffixes"
            )
        }
    }
}