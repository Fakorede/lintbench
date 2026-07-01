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
import org.jetbrains.uast.UCallExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (!containingClass.startsWith("kotlin.io.FilePathComponents")) return

        val args = node.valueArguments
        val suffix = when {
            node.receiver != null && args.size == 1 -> args[0]
            node.receiver == null && args.size == 2 -> args[1]
            else -> return
        }

        val value = ConstantEvaluator.evaluate(context, suffix) as? String ?: return
        if (value.startsWith('.')) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `File.endsWith` to check a file extension will not work as expected; " +
                    "it checks whole path components, not string suffixes. " +
                    "Use `file.path.endsWith(...)` or `file.extension == ...` instead."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components,
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will
                return false. If you want to check a file extension, use `file.path.endsWith(...)`
                or `file.extension` instead.
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