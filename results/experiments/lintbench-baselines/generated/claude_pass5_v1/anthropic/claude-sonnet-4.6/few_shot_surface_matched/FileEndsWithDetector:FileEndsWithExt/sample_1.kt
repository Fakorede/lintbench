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
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val FILE_ENDS_WITH_EXT = Issue.create(
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

        private const val FILE_CLASS = "java.io.File"
        private const val METHOD_ENDS_WITH = "endsWith"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(METHOD_ENDS_WITH)

    override fun getApplicableReferenceNames(): List<String> = listOf(METHOD_ENDS_WITH)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement
    ) {
        // Handled via visitMethodCall
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator

        // Check that the method is called on a java.io.File instance
        if (!evaluator.isMemberInClass(method, FILE_CLASS) &&
            !evaluator.isMemberInSubClassOf(method, FILE_CLASS)
        ) {
            return
        }

        // Check that the argument looks like a file extension (starts with a dot)
        val argument = node.valueArguments.firstOrNull() ?: return
        val argumentText = argument.asSourceString().trim('"', '\'', ' ')
        val looksLikeExtension = argumentText.startsWith(".")

        val message = if (looksLikeExtension) {
            "`File.endsWith` checks whole path components, not string suffixes; " +
                "`File(\"foo.txt\").endsWith(\".txt\")` is false. " +
                "Did you mean `file.path.endsWith(\"${argumentText}\")` " +
                "or `file.extension.equals(\"${argumentText.removePrefix(".")}\")`?"
        } else {
            "`File.endsWith` checks whole path components, not string suffixes. " +
                "Did you mean `file.path.endsWith(...)` instead?"
        }

        context.report(
            FILE_ENDS_WITH_EXT,
            node,
            context.getLocation(node),
            message
        )
    }
}