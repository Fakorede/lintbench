package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
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
                The Kotlin extension `File.endsWith(suffix)` checks whether the file's \
                path ends with the given path component, not whether the file name string \
                ends with the given suffix. For example, `File("foo.txt").endsWith(".txt")` \
                returns `false`.

                To check a file extension, use `file.path.endsWith(".txt")` or \
                `file.extension == "txt"`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        // Not needed; method calls are handled by visitMethodCall.
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val receiverType = node.receiverType ?: return
        if (context.evaluator.typeClass(receiverType)?.qualifiedName != "java.io.File") {
            return
        }

        val args = node.valueArguments
        if (args.size != 1) {
            return
        }

        if (!isStringType(context, args[0])) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Suspicious use of `File.endsWith(...)` to check a file extension; this checks path components, not string suffixes",
        )
    }

    private fun isStringType(context: JavaContext, expression: UExpression): Boolean {
        val type = expression.getExpressionType() ?: return false
        return isStringType(context, type)
    }

    private fun isStringType(context: JavaContext, type: PsiType): Boolean {
        val cls: PsiClass = context.evaluator.typeClass(type) ?: return false
        return cls.qualifiedName == "java.lang.String" ||
                cls.qualifiedName == "kotlin.String"
    }
}