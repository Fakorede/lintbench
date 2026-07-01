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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.evaluate

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
                The Kotlin extension method `File.endsWith(suffix)` checks whether the file path ends with a complete path component, not whether the file name ends with the given string suffix. This means that `File("foo.txt").endsWith(".txt")` will return `false`.

                If you want to test the suffix of the path string, use `file.path.endsWith(suffix)` or `file.name.endsWith(suffix)`. If you want to test the file extension, compare `file.extension` without a leading dot, for example `file.extension == "txt"`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun getApplicableReferenceNames(): List<String>? = listOf("extension")

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        val qualified = reference.uastParent as? UQualifiedReferenceExpression ?: return
        if (!isJavaIoFile(qualified.receiver)) return

        var current: UElement? = qualified
        var parent: UElement? = qualified.uastParent
        while (parent != null) {
            when (parent) {
                is UCallExpression -> {
                    if (parent.methodName == "equals") {
                        val argument = parent.valueArguments.firstOrNull()
                        if (argument != null) {
                            reportWrongExtensionLiteral(context, argument, parent)
                        }
                    }
                    return
                }
                is UBinaryExpression -> {
                    val operator = parent.operator
                    if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
                        val other = if (parent.left == current) parent.right else if (parent.right == current) parent.left else null
                        if (other != null) {
                            reportWrongExtensionLiteral(context, other, parent)
                        }
                    }
                    return
                }
            }
            current = parent
            parent = parent.uastParent
        }
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (node.methodName != "endsWith") return
        if (!isJavaIoFile(node.receiver)) return

        val argument = node.valueArguments.firstOrNull() ?: return
        val value = argument.evaluate() as? String ?: return
        if (!value.startsWith(".")) return

        val extension = value.substring(1)
        val message = "File.endsWith checks whole path components, not string suffixes; " +
                "did you mean file.path.endsWith(\"$value\") or file.extension == \"$extension\"?"
        context.report(ISSUE, node, context.getLocation(node), message)
    }

    private fun reportWrongExtensionLiteral(
        context: JavaContext, argument: UExpression, target: UElement,
    ) {
        val value = argument.evaluate() as? String ?: return
        if (!value.startsWith(".")) return

        val extension = value.substring(1)
        val message = "file.extension does not include the leading \".\"; " +
                "use \"$extension\" instead of \"$value\""
        context.report(ISSUE, target, context.getLocation(target), message)
    }

    private fun isJavaIoFile(expression: UExpression?): Boolean {
        if (expression == null) return false
        val type = expression.getExpressionType() ?: return false
        return type.canonicalText.startsWith("java.io.File")
    }
}