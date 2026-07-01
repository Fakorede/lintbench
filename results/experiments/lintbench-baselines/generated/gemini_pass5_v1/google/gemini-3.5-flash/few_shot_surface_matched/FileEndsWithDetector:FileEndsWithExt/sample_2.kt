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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File.endsWith checks whole path components, not suffixes",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames() = listOf("endsWith")

    override fun getApplicableReferenceNames() = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = node.receiverType
        val isFileReceiver = receiverType?.canonicalText == "java.io.File" ||
                context.evaluator.isMemberInSubClassOf(method, "java.io.File") ||
                method.parameters.firstOrNull()?.type?.canonicalText == "java.io.File"

        if (!isFileReceiver) return

        val argument = node.valueArguments.firstOrNull() ?: return
        val stringValue = getConstantStringValue(argument)
        if (stringValue != null && stringValue.startsWith(".")) {
            context.report(
                Incident(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using `File.endsWith` checks whole path components, not string suffixes. Use `path.endsWith` or `extension.equals` instead."
                )
            )
        }
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        val parent = reference.uastParent
        if (parent is UCallExpression && parent.methodName == "endsWith") {
            return
        }

        if (referenced is PsiMethod && referenced.name == "endsWith") {
            val isFileEndsWith = referenced.parameters.firstOrNull()?.type?.canonicalText == "java.io.File" ||
                    referenced.containingClass?.qualifiedName == "java.io.File"
            if (isFileEndsWith) {
                context.report(
                    Incident(
                        ISSUE,
                        reference,
                        context.getLocation(reference),
                        "Using `File.endsWith` checks whole path components, not string suffixes. Use `path.endsWith` or `extension.equals` instead."
                    )
                )
            }
        }
    }

    private fun getConstantStringValue(node: UExpression): String? {
        val evaluated = node.evaluate() as? String
        if (evaluated != null) return evaluated

        if (node is UCallExpression) {
            val method = node.resolve()
            if (method?.isConstructor == true && method.containingClass?.qualifiedName == "java.io.File") {
                val arg = node.valueArguments.firstOrNull() ?: return null
                return arg.evaluate() as? String
            }
        }
        return null
    }
}