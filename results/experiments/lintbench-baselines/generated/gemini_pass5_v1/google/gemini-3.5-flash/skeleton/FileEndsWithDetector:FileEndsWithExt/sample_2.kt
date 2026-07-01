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
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will \
                return false. Instead you might have intended `file.path.endsWith` or \
                `file.extension.equals`.
            """.trimIndent(),
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
        // No-op
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.name != "endsWith") return

        val receiverType = node.receiverType
        val isFile = receiverType?.canonicalText == "java.io.File" ||
                (method.parameterList.parametersCount > 0 &&
                 method.parameterList.parameters[0].type.canonicalText == "java.io.File")

        if (!isFile) return

        val args = node.valueArguments
        if (args.isNotEmpty()) {
            val arg = args.last()
            val value = arg.evaluate() as? String
            if (value != null && value.startsWith(".")) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using `File.endsWith` with a file extension suffix is likely a bug. It matches complete path segments, not string suffixes. Use `file.path.endsWith(...)` or `file.extension` instead."
                )
            }
        }
    }
}