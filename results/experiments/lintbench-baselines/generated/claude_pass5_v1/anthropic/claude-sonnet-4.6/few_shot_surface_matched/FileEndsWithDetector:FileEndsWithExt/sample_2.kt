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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.util.isMethodCall

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FILE_CLASS = "java.io.File"

        @JvmField
        val FILE_ENDS_WITH_EXT =
            Issue.create(
                id = "FileEndsWithExt",
                briefDescription = "File endsWith on file extensions",
                explanation =
                    """
                        The Kotlin extension method `File.endsWith(suffix)` checks whole path \
                        components, not just string suffixes. This means that \
                        `File("foo.txt").endsWith(".txt")` will return false. Instead you might \
                        have intended `file.path.endsWith` or `file.extension.equals`.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                implementation =
                    Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE),
                androidSpecific = false,
            )

        private const val ENDS_WITH = "endsWith"
        private const val EXTENSION = "extension"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(ENDS_WITH)

    override fun getApplicableReferenceNames(): List<String> = listOf(EXTENSION)

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiMethod,
    ) {
        // We're looking for accesses to File.extension (Kotlin extension property on File)
        // to suggest it as the fix alternative; but primarily we flag `endsWith` calls.
        // Nothing to report on bare `extension` reference itself.
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name != ENDS_WITH) return

        val evaluator = context.evaluator

        // Check if the receiver is a java.io.File
        val receiver = node.receiver ?: getImplicitReceiver(node) ?: return

        val receiverType = receiver.getExpressionType() ?: return
        val receiverTypeName = receiverType.canonicalText

        if (receiverTypeName != FILE_CLASS) return

        // Check that the argument looks like a file extension (starts with a dot)
        // or that this is the File.endsWith(File/String) that checks path components.
        // We want to flag any call to File.endsWith where the argument is a String
        // that starts with "." (likely a file extension check).
        val argument = node.valueArguments.firstOrNull()

        val argumentType = argument?.getExpressionType()?.canonicalText

        // File.endsWith(File) and File.endsWith(String) both check path components.
        // We flag both, but especially when the argument is a String starting with "."
        // which strongly indicates a file extension check.
        val isDotExtensionArg = isDotExtensionArgument(argument)

        if (argumentType == null) return

        val isStringArg = argumentType == "java.lang.String" || argumentType == "String"
        val isFileArg = argumentType == FILE_CLASS

        if (!isStringArg && !isFileArg) return

        // Only flag when the argument looks like an extension (starts with ".")
        // or is a String argument (could be an extension check)
        if (isStringArg) {
            val message =
                if (isDotExtensionArg) {
                    "`File.endsWith` checks whole path components, not string suffixes; " +
                        "`File(\"foo.txt\").endsWith(\".txt\")` returns `false`. " +
                        "Did you mean `file.path.endsWith(...)` or `file.extension.equals(...)`?"
                } else {
                    "`File.endsWith` checks whole path components, not string suffixes. " +
                        "Did you mean `file.path.endsWith(...)` or `file.extension.equals(...)`?"
                }
            context.report(
                FILE_ENDS_WITH_EXT,
                node,
                context.getLocation(node),
                message,
            )
        } else if (isFileArg) {
            val message =
                "`File.endsWith` checks whole path components, not string suffixes. " +
                    "Did you mean `file.path.endsWith(...)` or `file.extension.equals(...)`?"
            context.report(
                FILE_ENDS_WITH_EXT,
                node,
                context.getLocation(node),
                message,
            )
        }
    }

    /**
     * Checks whether the argument expression is a string literal that starts with ".",
     * which strongly suggests it's being used as a file extension check.
     */
    private fun isDotExtensionArgument(argument: UExpression?): Boolean {
        if (argument == null) return false
        val evaluated = argument.evaluate()
        if (evaluated is String) {
            return evaluated.startsWith(".")
        }
        return false
    }

    /**
     * Attempts to find an implicit receiver for the call expression by walking up the
     * UAST tree to find a qualified reference expression.
     */
    private fun getImplicitReceiver(node: UCallExpression): UExpression? {
        val parent = node.uastParent
        if (parent is UQualifiedReferenceExpression) {
            return parent.receiver
        }
        return null
    }
}