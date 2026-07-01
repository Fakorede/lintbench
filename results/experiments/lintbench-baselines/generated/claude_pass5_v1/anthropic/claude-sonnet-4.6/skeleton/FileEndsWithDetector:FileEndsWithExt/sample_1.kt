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
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will \
                return false. Instead you might have intended `file.path.endsWith` or \
                `file.extension.equals`.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ENDS_WITH = "endsWith"
        private const val FILE_CLASS = "java.io.File"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(ENDS_WITH)

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        // Not used
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check that the method is called on a java.io.File receiver
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        // The Kotlin extension endsWith on File is defined as an extension in kotlin stdlib,
        // but the receiver type should be java.io.File
        if (qualifiedName != FILE_CLASS) {
            // Also check if this might be the Kotlin extension function on File
            // The extension is in kotlin.io package and the containing class might differ
            val receiverType = node.receiverType
            val receiverTypeName = receiverType?.canonicalText
            if (receiverTypeName != FILE_CLASS) {
                return
            }
        }

        // Check that the argument is a string that starts with a dot (looks like a file extension)
        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]
        val argValue = firstArg.evaluate()

        if (argValue is String && argValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks whole path components, not string suffixes; " +
                    "`File(\"foo.txt\").endsWith(\".txt\")` is false. " +
                    "Did you mean `file.path.endsWith(\"${argValue}\")` " +
                    "or `file.extension.equals(\"${argValue.removePrefix(".")}\")`?",
            )
        } else {
            // Even if we can't evaluate the argument statically, warn that this may be
            // a misuse if it looks like an extension check pattern
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks whole path components, not string suffixes; " +
                    "`File(\"foo.txt\").endsWith(\".txt\")` is false. " +
                    "Did you mean `file.path.endsWith(suffix)` " +
                    "or `file.extension.equals(suffix)`?",
            )
        }
    }
}