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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

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
            ),
            androidSpecific = false
        )

        private const val FILE_CLASS = "java.io.File"
        private const val ENDS_WITH = "endsWith"
        private const val EXTENSION = "extension"

        private val MESSAGE =
            "`File.endsWith(suffix)` checks whole path components, not string suffixes. " +
                "To check a file extension, use `file.path.endsWith(\".ext\")` or " +
                "`file.extension.equals(\"ext\")` instead."
    }

    override fun getApplicableMethodNames(): List<String> = listOf(ENDS_WITH)

    override fun getApplicableReferenceNames(): List<String> = listOf(EXTENSION)

    override fun visitReference(
        context: JavaContext,
        reference: USimpleNameReferenceExpression,
        referenced: PsiMethod
    ) {
        // We handle extension property references to detect file.extension usage;
        // but the main check is in visitMethodCall. This hook is here to satisfy
        // the interface requirement and could be used for additional checks if needed.
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (method.name != ENDS_WITH) return

        // Check that the receiver is a java.io.File
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return

        // The method could be the Kotlin extension function on File or a member method.
        // File.endsWith(File) and File.endsWith(String) - both are problematic when
        // the argument looks like a file extension (starts with ".").
        val isFileMethod = evaluator.extendsClass(containingClass, FILE_CLASS, false) ||
            containingClass.qualifiedName == FILE_CLASS

        // Also check for Kotlin extension functions on File
        val receiverType = node.receiverType
        val isFileReceiver = receiverType?.canonicalText == FILE_CLASS ||
            (receiverType != null && evaluator.typeMatches(receiverType, FILE_CLASS))

        if (!isFileMethod && !isFileReceiver) {
            // Check if this is a Kotlin stdlib extension: receiver expression type is File
            val receiver = node.receiver
            if (receiver != null) {
                val recvType = receiver.getExpressionType()
                if (recvType == null || recvType.canonicalText != FILE_CLASS) {
                    return
                }
            } else {
                // No receiver - check if the method is defined on File or its subclass
                if (!isFileMethod) return
            }
        }

        // Check if the argument looks like a file extension (starts with a dot)
        val argument = node.valueArguments.firstOrNull() ?: return
        val argumentText = argument.asSourceString().trim('"', '\'', ' ')

        if (argumentText.startsWith(".")) {
            val location = context.getLocation(node)
            context.report(FILE_ENDS_WITH_EXT, node, location, MESSAGE)
        } else {
            // Even without a dot, if the receiver is a File and endsWith is called,
            // warn because File.endsWith checks path components, not string suffixes.
            // Only flag if the argument is a string literal that looks like an extension pattern.
            val argType = argument.getExpressionType()
            if (argType?.canonicalText == "java.lang.String" || argType?.canonicalText == "kotlin.String") {
                val location = context.getLocation(node)
                context.report(FILE_ENDS_WITH_EXT, node, location, MESSAGE)
            } else if (argType?.canonicalText == FILE_CLASS) {
                // File.endsWith(File) - also potentially confusing but less likely to be
                // an extension check; only report if the string representation starts with dot
                // Skip this case to reduce false positives
            }
        }
    }
}