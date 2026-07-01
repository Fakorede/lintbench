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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File.endsWith checks path components, not string suffixes",
            explanation = "The `File.endsWith(suffix)` method checks whole path components, not just string suffixes. This means that `File(\"foo.txt\").endsWith(\".txt\")` will return false. Instead, use `file.path.endsWith(suffix)` or `file.extension == suffix`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun getApplicableReferenceNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = context.evaluator.getType(node.receiver) ?: return
        if (receiverType.canonicalText != "java.io.File") return

        val message = "File.endsWith(String) checks path components, not string suffixes. Use file.path.endsWith() or check file.extension instead."
        context.report(Incident(ISSUE, node, context.getLocation(node), message))
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, resolved: PsiElement) {
        if (resolved is PsiMethod && resolved.name == "endsWith") {
            val qualifier = (reference as? UQualifiedReferenceExpression)?.receiver
            val receiverType = if (qualifier != null) context.evaluator.getType(qualifier) else null
            if (receiverType?.canonicalText == "java.io.File") {
                val message = "File.endsWith(String) checks path components, not string suffixes. Use file.path.endsWith() or check file.extension instead."
                context.report(Incident(ISSUE, reference, context.getLocation(reference), message))
            }
        }
    }
}