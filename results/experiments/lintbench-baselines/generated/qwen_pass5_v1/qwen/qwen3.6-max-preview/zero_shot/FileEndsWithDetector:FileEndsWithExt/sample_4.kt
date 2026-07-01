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

    override fun getApplicableMethodNames(): List<String>? = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiver = node.receiver ?: return
        val receiverType = context.evaluator.getType(receiver) ?: return
        if (receiverType.canonicalText.removeSuffix("?") == "java.io.File" && node.valueArguments.size == 1) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "`File.endsWith()` checks whole path components, not string suffixes. " +
                    "Use `file.path.endsWith()` or check `file.extension` instead."
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = "The Kotlin extension method `File.endsWith(suffix)` checks whole path components,  not just string suffixes. This means that `File(\"foo.txt\").endsWith(\".txt\")` will return  false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}