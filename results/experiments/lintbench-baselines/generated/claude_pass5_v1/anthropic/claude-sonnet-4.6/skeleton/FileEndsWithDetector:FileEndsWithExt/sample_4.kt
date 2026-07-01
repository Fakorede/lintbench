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
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ENDS_WITH = "endsWith"
        private const val FILE_CLASS = "java.io.File"

        private val MESSAGE = """
            `File.endsWith` checks whole path components, not string suffixes. \
            For extension checks, use `file.path.endsWith(".ext")` or `file.extension.equals("ext")`.
        """.trimIndent()
    }

    override fun getApplicableMethodNames(): List<String> = listOf(ENDS_WITH)

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        // Not used
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check that the method is File.endsWith
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, FILE_CLASS, false)) {
            return
        }

        // Get the argument to endsWith — we're looking for a string starting with "."
        // which strongly suggests the caller intended a string suffix check for a file extension
        val arguments = node.valueArguments
        if (arguments.size != 1) return

        val argument = arguments[0]
        val argumentValue = argument.evaluate()

        if (argumentValue is String && argumentValue.startsWith(".")) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "`File.endsWith` checks whole path components, not string suffixes. " +
                    "For extension checks, use `file.path.endsWith(\"${argumentValue}\")` " +
                    "or `file.extension.equals(\"${argumentValue.removePrefix(".")}\")`.",
            )
        }
    }
}