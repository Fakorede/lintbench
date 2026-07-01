package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            FileEndsWithDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = "The `File.endsWith(suffix)` method checks if the path ends with the given suffix as a complete path component, not as a simple string suffix. This means that `File(\"foo.txt\").endsWith(\".txt\")` will return false. Instead, use `file.path.endsWith(suffix)` or check `file.extension`.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("endsWith")

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        // Not used for this detector
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != "java.io.File") return

        val args = node.valueArguments
        if (args.size != 1) return

        val suffix = context.evaluator.evaluateString(args[0])
        if (suffix != null && suffix.startsWith(".")) {
            val extension = suffix.removePrefix(".")
            context.report(
                ISSUE,
                context.getLocation(node),
                "File.endsWith checks whole path components, not string suffixes. " +
                    "Use `file.path.endsWith(\"$suffix\")` or `file.extension == \"$extension\"` instead."
            )
        }
    }
}