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
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will \
                return false. Instead you might have intended `file.path.endsWith` or \
                `file.extension.equals`.
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

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // We only care about calls on java.io.File receivers
        val receiverType = node.receiverType ?: return
        if (receiverType.canonicalText != "java.io.File") return

        // Check that the argument starts with a dot (looks like a file extension)
        val arguments = node.valueArguments
        if (arguments.size != 1) return

        val arg = arguments[0]
        val argValue = arg.evaluate()
        if (argValue is String && argValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks whole path components, not string suffixes; " +
                        "`file.endsWith(\".txt\")` will always return false. " +
                        "Did you mean `file.path.endsWith(\"${argValue}\")` or " +
                        "`file.extension.equals(\"${argValue.removePrefix(".")}\")`?"
            )
        }
    }
}