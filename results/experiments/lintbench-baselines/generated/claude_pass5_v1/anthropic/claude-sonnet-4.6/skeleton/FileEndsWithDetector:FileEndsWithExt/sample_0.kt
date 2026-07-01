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
        // Check that the method is endsWith
        if (method.name != ENDS_WITH) return

        // Check that the receiver type is java.io.File
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return
        if (!evaluator.inheritsFrom(containingClass, FILE_CLASS, false)) return

        // Get the argument - we're looking for a string argument that starts with a dot
        // (indicating a file extension check)
        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val argument = arguments[0]
        val argumentValue = argument.evaluate()

        // Check if the argument looks like a file extension (starts with a dot)
        if (argumentValue is String && argumentValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks whole path components, not string suffixes; " +
                    "`File(\"foo.txt\").endsWith(\".txt\")` is false. " +
                    "Did you mean `file.path.endsWith(\"${argumentValue}\")` " +
                    "or `file.extension.equals(\"${argumentValue.removePrefix(".")}\")`?",
            )
        } else {
            // Even without a dot prefix, warn that File.endsWith behaves differently
            // than String.endsWith when called on a File receiver
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks whole path components, not string suffixes. " +
                    "Did you mean `file.path.endsWith(...)` instead?",
            )
        }
    }
}