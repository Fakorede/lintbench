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
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File.endsWith called with file extension",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                java.util.EnumSet.of(Scope.JAVA_FILE_SCOPE)
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun getApplicableReferenceNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!isFileEndsWith(method)) return

        val argument = node.valueArguments.firstOrNull() ?: return
        val argType = argument.getExpressionType()?.canonicalText
        if (argType == "java.lang.String") {
            val constantValue = argument.evaluate() as? String
            if (constantValue != null && constantValue.startsWith(".")) {
                val location = context.getLocation(argument)
                val message = "Kotlin's `File.endsWith(suffix)` checks whole path components, not file extensions (e.g. \"$constantValue\"). Use `file.path.endsWith(\"$constantValue\")` or `file.extension == \"${constantValue.removePrefix(".")}\"` instead."
                context.report(Incident(ISSUE, node, location, message))
            }
        }
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiMethod && isFileEndsWith(referenced)) {
            val location = context.getLocation(reference)
            val message = "Usage of `File.endsWith` matches entire path components, not file extensions. Use `file.path.endsWith` or `file.extension` instead."
            context.report(Incident(ISSUE, reference, location, message))
        }
    }

    private fun isFileEndsWith(method: PsiMethod): Boolean {
        if (method.name != "endsWith") return false
        val containingClass = method.containingClass?.qualifiedName ?: return false
        if (containingClass.startsWith("kotlin.io.")) {
            val parameters = method.parameterList.parameters
            if (parameters.isNotEmpty() && parameters[0].type.canonicalText == "java.io.File") {
                return true
            }
        }
        return false
    }
}