package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastLiteralUtils

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val JAVA_IO_FILE = "java.io.File"
        private const val JAVA_LANG_STRING = "java.lang.String"

        @JvmField
        val FILE_ENDS_WITH_EXT = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "Suspicious `File.endsWith` usage for file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components,
                not just string suffixes. This means `File("foo.txt").endsWith(".txt")` returns false.

                If you want to test a file extension, use `file.path.endsWith(".txt")` or
                `file.extension.equals("txt")` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames() = listOf("endsWith")

    override fun getApplicableReferenceNames() = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!isFileEndsWithString(method)) return

        val argument = node.valueArguments.firstOrNull() ?: return
        if (!looksLikeFileExtension(argument)) return

        val message =
            "`File.endsWith(String)` checks path components, not string suffixes. " +
                "Use `file.path.endsWith(...)`, `file.extension`, or `file.extension.equals(...)` instead."

        context.report(
            Incident(
                FILE_ENDS_WITH_EXT,
                node,
                context.getLocation(node),
                message
            )
        )
    }

    override fun visitReference(context: JavaContext, node: UReferenceExpression) {
        if (node !is UCallableReferenceExpression) return

        val method = node.resolve() as? PsiMethod ?: return
        if (!isFileEndsWithString(method)) return

        val message =
            "`File::endsWith` checks path components, not string suffixes. " +
                "If used for file extensions, use `file.path.endsWith(...)`, `file.extension`, or `file.extension.equals(...)` instead."

        context.report(
            Incident(
                FILE_ENDS_WITH_EXT,
                node,
                context.getLocation(node),
                message
            )
        )
    }

    private fun isFileEndsWithString(method: PsiMethod): Boolean {
        if (method.name != "endsWith") return false

        val parameters = method.parameterList.parameters
        if (parameters.size != 2) return false
        if (parameters[0].type.canonicalText != JAVA_IO_FILE) return false
        if (parameters[1].type.canonicalText != JAVA_LANG_STRING) return false

        return true
    }

    private fun looksLikeFileExtension(expression: UExpression): Boolean {
        val value = UastLiteralUtils.getStringLiteralValue(expression) ?: return false
        return value.startsWith('.')
    }
}