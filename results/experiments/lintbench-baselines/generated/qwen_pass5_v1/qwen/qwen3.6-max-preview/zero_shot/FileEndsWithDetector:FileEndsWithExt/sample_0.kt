package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UastScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class FileEndsWithDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = node.receiverType ?: return
        if (!context.evaluator.typeMatches(receiverType, "java.io.File")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 1) return

        val argType = args[0].getExpressionType() ?: return
        if (!context.evaluator.typeMatches(argType, "java.lang.String")) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "File.endsWith checks whole path components, not string suffixes. " +
                    "Use file.path.endsWith or file.extension.equals instead."
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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