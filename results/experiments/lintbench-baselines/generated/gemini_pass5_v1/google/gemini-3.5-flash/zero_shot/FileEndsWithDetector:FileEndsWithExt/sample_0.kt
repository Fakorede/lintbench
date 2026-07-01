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

    override fun getApplicableMethodNames(): List<String> {
        return listOf("endsWith")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = node.receiverType ?: return
        if (receiverType.canonicalText != "java.io.File") {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return
        val firstArg = arguments[0]

        val constantValue = firstArg.evaluate()
        if (constantValue is String) {
            if (constantValue.startsWith(".")) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using `File.endsWith(\"$constantValue\")` checks path components, not the file extension. Did you mean `path.endsWith(...)` or `extension.equals(...)`?"
                )
            }
        }
    }
}