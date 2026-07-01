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
import org.jetbrains.uast.evaluateString

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiver = node.receiver ?: return
        val receiverType = receiver.getExpressionType() ?: return
        if (!context.evaluator.typeMatches(receiverType, "java.io.File")) return

        val arg = node.valueArguments.firstOrNull() ?: return
        val argType = arg.getExpressionType() ?: return
        if (!context.evaluator.typeMatches(argType, "java.lang.String")) return

        val suffix = arg.evaluateString() ?: return
        if (!suffix.startsWith(".")) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "File.endsWith(\"$suffix\") checks whole path components, not string suffixes. " +
                    "Use file.path.endsWith(\"$suffix\") or file.extension == \"${suffix.removePrefix(".")}\" instead."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path \
                components, not just string suffixes. This means that \
                `File("foo.txt").endsWith(".txt")` returns false. Instead you should \
                use `file.path.endsWith(...)` or compare `file.extension`.
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