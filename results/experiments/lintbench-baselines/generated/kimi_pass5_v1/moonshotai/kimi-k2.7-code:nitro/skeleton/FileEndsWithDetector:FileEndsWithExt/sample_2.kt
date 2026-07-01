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
                The Kotlin extension method `File.endsWith(suffix)` checks whole path
                components, not string suffixes. Calling
                `File("foo.txt").endsWith(".txt")` returns `false`. If you want to test
                the file extension, use `file.path.endsWith(suffix)` or
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
        // No references are checked; method calls are handled below.
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.name != "endsWith") {
            return
        }

        if (!isFileEndsWithMethod(context, node, method)) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val value = argument.evaluate() as? String ?: return
        if (!value.startsWith(".")) {
            return
        }

        val extension = value.removePrefix(".")
        val message = "Suspicious `File.endsWith(\"$value\")`: File.endsWith checks " +
                "path components, not string suffixes. Did you mean " +
                "`file.path.endsWith(\"$value\")` or `file.extension == \"$extension\"`?"

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            message,
        )
    }

    private fun isFileEndsWithMethod(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ): Boolean {
        val receiver = node.receiver
        if (receiver != null) {
            val receiverType = context.evaluator.getType(receiver)?.canonicalText
            if (receiverType == "java.io.File" || receiverType == "java.io.File?") {
                return true
            }
        }

        // Kotlin extension functions are compiled to static methods in kotlin.io.*.
        val containingClass = method.containingClass?.qualifiedName
        if (containingClass?.startsWith("kotlin.io.") == true) {
            val parameters = method.parameterList.parameters
            return parameters.size == 2 &&
                    parameters[0].type.canonicalText == "java.io.File" &&
                    parameters[1].type.canonicalText == "java.lang.String"
        }

        return false
    }
}