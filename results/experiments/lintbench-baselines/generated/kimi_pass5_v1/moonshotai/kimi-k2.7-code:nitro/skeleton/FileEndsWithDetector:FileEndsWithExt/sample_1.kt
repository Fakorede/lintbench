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
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getExpressionType

private const val JAVA_IO_FILE = "java.io.File"

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
                The Kotlin `File.endsWith` extension checks whether the file's path \
                ends with the given path component, not whether the file name ends \
                with the given string suffix. For example, \
                `File("foo.txt").endsWith(".txt")` returns `false` because ".txt" \
                is not a path component. Use `file.path.endsWith(".txt")` or \
                `file.extension == "txt"` instead.
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
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        // Not used.
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.name != "endsWith") {
            return
        }

        val receiverType = node.receiver?.getExpressionType() ?: return
        val receiverClass = context.evaluator.getTypeClass(receiverType) ?: return
        if (receiverClass.qualifiedName != JAVA_IO_FILE) {
            return
        }

        if (node.valueArgumentCount != 1) {
            return
        }

        val suffix = node.valueArguments.firstOrNull()?.evaluateString() ?: return
        if (!suffix.startsWith('.')) {
            return
        }

        val message = buildString {
            append("File.endsWith() checks whole path components, not string suffixes. ")
            append("Use file.path.endsWith(\"$suffix\") or file.extension == \"")
            append(suffix.substring(1))
            append("\" instead.")
        }

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = message,
        )
    }
}