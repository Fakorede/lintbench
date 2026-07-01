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
            explanation = "The Kotlin extension method `File.endsWith(suffix)` checks whole path components, " +
                    "not just string suffixes. This means that `File(\"foo.txt\").endsWith(\".txt\")` will " +
                    "return false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("endsWith")
    }

    override fun getApplicableReferenceNames(): List<String>? {
        return null
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        // No-op
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.name != "endsWith") return

        if (!isFileOrPath(context, node, method)) return

        val suffixArg = if (node.valueArgumentCount == 1) {
            node.valueArguments[0]
        } else if (node.valueArgumentCount == 2) {
            node.valueArguments[1]
        } else {
            null
        } ?: return

        val constant = suffixArg.evaluate()
        if (constant is String && constant.startsWith(".") && constant.length > 1) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `File.endsWith` with a file extension suffix (`\"$constant\"`) will look for a full path component, not a file extension. Use `file.path.endsWith(...)` or `file.extension` instead."
            )
        }
    }

    private fun isFileOrPath(context: JavaContext, node: UCallExpression, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        val containingClass = method.containingClass
        if (containingClass != null) {
            val qName = containingClass.qualifiedName
            if (qName == "java.io.File" || qName == "java.nio.file.Path") {
                return true
            }
        }
        val receiverType = node.receiverType
        if (receiverType != null) {
            if (evaluator.typeMatches(receiverType, "java.io.File") ||
                evaluator.typeMatches(receiverType, "java.nio.file.Path")) {
                return true
            }
        }
        val parameters = method.parameterList.parameters
        if (parameters.isNotEmpty()) {
            val firstParamType = parameters[0].type
            if (evaluator.typeMatches(firstParamType, "java.io.File") ||
                evaluator.typeMatches(firstParamType, "java.nio.file.Path")) {
                return true
            }
        }
        return false
    }
}