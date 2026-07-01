package com.android.tools.lint.checks

import com.android.tools.lint.client.api.JavaElementVisitor
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getExpressionType

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        visitor: JavaElementVisitor?
    ) {
        if (!isFileEndsWithString(context, node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "File.endsWith checks whole path components, not string suffixes. " +
                "Use file.path.endsWith(...) or file.extension.equals(...) instead."
        )
    }

    private fun isFileEndsWithString(context: JavaContext, node: UCallExpression): Boolean {
        if (node.methodName != "endsWith") {
            return false
        }

        val receiver = node.receiver ?: return false
        val receiverType = receiver.getExpressionType() ?: return false
        if (!context.evaluator.typeMatches(receiverType, "java.io.File")) {
            return false
        }

        val method = node.resolve() as? PsiMethod ?: return false
        val containingClass = method.containingClass?.qualifiedName ?: return false
        if (!containingClass.startsWith("kotlin.io.")) {
            return false
        }

        val params = method.parameterList.parameters
        if (params.isEmpty()) {
            return false
        }

        return params.last().type.canonicalText == "java.lang.String"
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method <code>File.endsWith(suffix)</code> checks whole path
                components, not just string suffixes. This means that
                <code>File("foo.txt").endsWith(".txt")</code> will return false. Instead you
                might have intended <code>file.path.endsWith</code> or
                <code>file.extension.equals</code>.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}