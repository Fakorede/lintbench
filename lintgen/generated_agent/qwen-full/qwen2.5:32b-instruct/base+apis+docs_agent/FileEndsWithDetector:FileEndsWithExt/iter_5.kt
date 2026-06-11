package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "FileEndsWith",
            briefDescription = "Use of `File.endsWith(suffix)` on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return false.
                
                Instead you might have intended to use `file.path.endsWith` or `file.extension.equals`.
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

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("endsWith")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        val receiver = node.receiver as? UQualifiedReferenceExpression ?: return

        if (receiver.selectorName == "endsWith" && node.valueArgumentCount > 0) {
            val qualifierType = context.evaluator.getType(receiver.qualifier)
            if (qualifierType?.canonicalText == "java.io.File") {
                reportIssue(context, node)
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        val message = "Use `file.path.endsWith` or `file.extension.equals` instead of `File.endsWith(suffix)`"
        context.report(ISSUE, node, context.getLocation(node), message)
    }
}