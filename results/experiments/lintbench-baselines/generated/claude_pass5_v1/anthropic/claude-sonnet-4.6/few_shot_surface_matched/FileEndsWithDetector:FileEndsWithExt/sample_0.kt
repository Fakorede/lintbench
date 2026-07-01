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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

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
        private const val EXTENSION = "extension"

        private val MESSAGE =
            "`File.endsWith(String)` checks whole path components, not string suffixes. " +
                "To check a file extension, use `file.path.endsWith(\".ext\")` or " +
                "`file.extension.equals(\"ext\")` instead."
    }

    override fun getApplicableMethodNames(): List<String> = listOf(ENDS_WITH)

    override fun getApplicableReferenceNames(): List<String> = listOf(EXTENSION)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiMethod
    ) {
        // We check the extension property access to see if it's being called on a File.
        // This is informational context; the main check is in visitMethodCall.
        // However, if someone accesses file.extension, that's actually the correct pattern,
        // so we don't flag it here. We only use getApplicableReferenceNames to help
        // detect when the correct pattern is already being used (no action needed).
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (method.name != ENDS_WITH) return

        // Check that the receiver is a java.io.File
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, FILE_CLASS) &&
            !evaluator.isMemberInSubClassOf(method, FILE_CLASS)
        ) {
            // Also check Kotlin extension on File
            val containingClass = method.containingClass?.qualifiedName
            if (containingClass != FILE_CLASS &&
                !evaluator.extendsClass(
                    evaluator.findClass(containingClass ?: ""),
                    FILE_CLASS,
                    false
                )
            ) {
                // Check if receiver type is File
                val receiverType = node.receiverType?.canonicalText ?: ""
                if (receiverType != FILE_CLASS && !receiverType.startsWith("$FILE_CLASS ")) {
                    return
                }
            }
        }

        // Check if the argument looks like a file extension (starts with a dot)
        val argument = node.valueArguments.firstOrNull()
        if (argument is ULiteralExpression) {
            val value = argument.value
            if (value is String && value.startsWith(".")) {
                context.report(
                    FILE_ENDS_WITH_EXT,
                    node,
                    context.getLocation(node),
                    MESSAGE
                )
                return
            }
        }

        // Even if the argument doesn't start with a dot, warn if the receiver is a File,
        // since File.endsWith checks path components, not string suffixes.
        val receiverType = node.receiverType?.canonicalText ?: ""
        if (receiverType == FILE_CLASS || isFileReceiver(context, node)) {
            context.report(
                FILE_ENDS_WITH_EXT,
                node,
                context.getLocation(node),
                MESSAGE
            )
        }
    }

    private fun isFileReceiver(context: JavaContext, node: UCallExpression): Boolean {
        val evaluator = context.evaluator
        val receiverType = node.receiverType ?: return false
        val canonicalText = receiverType.canonicalText
        if (canonicalText == FILE_CLASS) return true
        val psiClass = evaluator.findClass(canonicalText) ?: return false
        return evaluator.extendsClass(psiClass, FILE_CLASS, false)
    }
}