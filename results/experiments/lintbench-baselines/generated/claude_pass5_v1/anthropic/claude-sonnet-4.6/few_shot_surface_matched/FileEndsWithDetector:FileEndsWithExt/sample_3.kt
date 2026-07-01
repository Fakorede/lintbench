package com.android.tools.lint.checks

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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.util.isMethodCall

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val FILE_ENDS_WITH_EXT = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will \
                return false. Instead you might have intended `file.path.endsWith` or \
                `file.extension.equals`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val FILE_CLASS = "java.io.File"
        private const val ENDS_WITH = "endsWith"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(ENDS_WITH)

    override fun getApplicableReferenceNames(): List<String> = listOf(ENDS_WITH)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiMethod
    ) {
        // Only handle if this reference is NOT already part of a call expression
        // (to avoid double-reporting with visitMethodCall)
        val parent = reference.uastParent
        if (parent != null && parent.isMethodCall()) {
            return
        }

        if (!isFileEndsWithMethod(context, referenced)) {
            return
        }

        reportIssue(context, reference)
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!isFileEndsWithMethod(context, method)) {
            return
        }

        // Check if the argument looks like a file extension (starts with a dot)
        val argument = node.valueArguments.firstOrNull()
        if (argument != null && !argumentLooksLikeExtension(context, argument)) {
            return
        }

        reportIssue(context, node)
    }

    private fun isFileEndsWithMethod(context: JavaContext, method: PsiMethod): Boolean {
        if (method.name != ENDS_WITH) {
            return false
        }
        val evaluator = context.evaluator
        // Check if the method is defined on java.io.File or is a Kotlin extension on File
        if (evaluator.isMemberInClass(method, FILE_CLASS)) {
            return true
        }
        // Also check for Kotlin extension functions on File (e.g. from kotlin stdlib or user code)
        val containingClass = method.containingClass
        if (containingClass != null) {
            val qualifiedName = containingClass.qualifiedName ?: ""
            if (qualifiedName.contains("File") || qualifiedName.contains("file")) {
                val params = method.parameterList.parameters
                if (params.isNotEmpty()) {
                    val firstParamType = params[0].type.canonicalText
                    if (firstParamType == "java.lang.String" || firstParamType == "java.io.File") {
                        return true
                    }
                }
            }
        }
        // Check receiver type for extension functions
        val receiverType = getReceiverType(method)
        if (receiverType == FILE_CLASS) {
            return true
        }
        return false
    }

    private fun getReceiverType(method: PsiMethod): String? {
        // For Kotlin extension functions, the first parameter is the receiver
        // We check if the method is an extension on File by examining annotations or parameter names
        val modifierList = method.modifierList
        // Check for @kotlin.jvm.JvmStatic or extension receiver annotations
        for (annotation in modifierList.annotations) {
            val qualifiedName = annotation.qualifiedName ?: continue
            if (qualifiedName.contains("ExtensionFunctionType")) {
                return null
            }
        }
        return null
    }

    private fun argumentLooksLikeExtension(context: JavaContext, argument: UExpression): Boolean {
        val evaluatedValue = argument.evaluate()
        if (evaluatedValue is String) {
            // If the argument starts with a dot, it looks like a file extension check
            return evaluatedValue.startsWith(".")
        }
        // If we can't evaluate it statically, still report (conservative approach)
        return true
    }

    private fun reportIssue(context: JavaContext, node: UElement) {
        val message = "`File.endsWith(String)` checks whole path components, not string suffixes. " +
            "`File(\"foo.txt\").endsWith(\".txt\")` returns false. " +
            "Did you mean `file.path.endsWith(\".txt\")` or `file.extension.equals(\"txt\")`?"
        context.report(
            FILE_ENDS_WITH_EXT,
            node,
            context.getLocation(node),
            message
        )
    }
}