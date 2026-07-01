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
        // Check that the method is called on a java.io.File receiver
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != FILE_CLASS) return

        // Check that the argument (if present) starts with a dot, indicating an extension check
        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]
        val argText = firstArg.asSourceString().trim('"', '\'', ' ')

        // Warn if the argument looks like a file extension (starts with a dot)
        // or in any case since File.endsWith checks path components, not string suffixes
        if (argText.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE,
            )
        } else {
            // Even without a leading dot, File.endsWith checks path components not string suffixes,
            // so any use could be misleading, but we focus on the extension check pattern.
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "`File.endsWith` checks whole path components, not string suffixes. " +
                    "If you meant to check a string suffix, use `file.path.endsWith(...)` instead.",
            )
        }
    }
}