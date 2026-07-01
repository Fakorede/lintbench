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
            explanation = "The `File.endsWith(suffix)` method checks whole path components, not just string suffixes. " +
                "This means that `File(\"foo.txt\").endsWith(\".txt\")` will return false. " +
                "Instead you might have intended `file.path.endsWith` or `file.extension.equals`.",
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
        // Not used for this detector; method calls are handled via visitMethodCall.
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInClass(method, "java.io.File")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 1) return

        val suffix = context.evaluator.evaluate(args[0]) as? String ?: return
        if (suffix.startsWith(".")) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "`File.endsWith` checks whole path components, not string suffixes. " +
                    "Use `file.path.endsWith` or check `file.extension` instead."
            )
        }
    }
}