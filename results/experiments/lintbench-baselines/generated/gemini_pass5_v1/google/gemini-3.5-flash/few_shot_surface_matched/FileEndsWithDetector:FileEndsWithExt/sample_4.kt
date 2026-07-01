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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File.endsWith(suffix) checks whole path components, not string suffixes",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun getApplicableReferenceNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        val isKotlinFileExtension = containingClass == "kotlin.io.FilesKt__UtilsKt" || containingClass == "kotlin.io.FilesKt"

        if (isKotlinFileExtension) {
            val lastArgument = node.valueArguments.lastOrNull() ?: return
            val argType = lastArgument.getExpressionType()?.canonicalText
            if (argType == "java.lang.String") {
                reportIssue(context, node)
            }
        }
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (reference.uastParent is UCallExpression) {
            return
        }
        if (referenced is PsiMethod) {
            val containingClass = referenced.containingClass?.qualifiedName ?: return
            val isKotlinFileExtension = containingClass == "kotlin.io.FilesKt__UtilsKt" || containingClass == "kotlin.io.FilesKt"
            if (isKotlinFileExtension) {
                reportIssue(context, reference)
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UElement) {
        context.report(
            Incident(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `file.path.endsWith()` or `file.extension.equals()` instead of `File.endsWith()` to check file extensions"
            )
        )
    }
}