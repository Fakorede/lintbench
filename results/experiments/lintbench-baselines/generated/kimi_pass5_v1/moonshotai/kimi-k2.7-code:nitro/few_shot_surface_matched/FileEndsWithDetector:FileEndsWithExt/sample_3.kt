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
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val FILE_ENDS_WITH_EXT = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "Suspicious file extension check using `File.endsWith`",
            explanation = """
                `File.endsWith(String)` checks whole path components, not string suffixes.
                For example, `File("foo.txt").endsWith(".txt")` returns false.
                If you want to test a file extension, use `file.path.endsWith(...)`
                or `file.extension.equals(...)` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun getApplicableReferenceNames(): List<String>? = emptyList()

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        // No references need to be flagged.
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (!containingClass.startsWith("kotlin.io.")) {
            return
        }

        val receiver = node.receiver ?: return
        val receiverClass = context.evaluator.getTypeClass(receiver.getExpressionType()) ?: return
        if (receiverClass.qualifiedName != "java.io.File") {
            return
        }

        val args = node.valueArguments
        if (args.size != 1) {
            return
        }

        val argType = args[0].getExpressionType()?.canonicalText ?: return
        if (argType != "java.lang.String" && argType != "kotlin.String") {
            return
        }

        val location = context.getLocation(node)
        val message =
            "`File.endsWith(String)` checks whole path components, not string suffixes. " +
                "Use `file.path.endsWith(...)` or `file.extension.equals(...)` instead."

        context.report(Incident(FILE_ENDS_WITH_EXT, node, location, message))
    }
}