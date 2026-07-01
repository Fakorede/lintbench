package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
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
                components, not just string suffixes. This means that
                `File("foo.txt").endsWith(".txt")` will return false. Instead you might
                have intended `file.path.endsWith` or `file.extension.equals`.
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
        // Not used by this detector.
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (node.valueArgumentCount != 1) return

        val receiver = node.receiver ?: return
        val receiverType = receiver.getExpressionType() ?: return
        val receiverClass = context.evaluator.getTypeClass(receiverType) ?: return
        val receiverClassName = receiverClass.qualifiedName ?: return
        if (receiverClassName != "java.io.File" && receiverClassName != "java.nio.file.Path") {
            return
        }

        val argument = node.valueArguments[0]
        val suffix = (argument as? ULiteralExpression)?.value as? String ?: return
        if (!suffix.startsWith(".")) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using `endsWith` on a file/path checks whole path components, not string suffixes; " +
                "did you mean `file.path.endsWith(\"$suffix\")` or " +
                "`file.extension == \"${suffix.removePrefix(".")}\"`?"
        )
    }
}