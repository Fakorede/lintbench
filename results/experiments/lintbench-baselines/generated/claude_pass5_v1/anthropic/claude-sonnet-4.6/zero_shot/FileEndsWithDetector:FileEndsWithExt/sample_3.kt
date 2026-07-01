package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
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
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        // Check that this is called on a java.io.File receiver
        val receiverType = node.receiverType ?: return
        if (receiverType.canonicalText != "java.io.File") return

        // Check that the argument looks like a file extension (starts with a dot)
        val arguments = node.valueArguments
        if (arguments.size != 1) return

        val arg = arguments[0]
        val argValue = when (arg) {
            is ULiteralExpression -> arg.value as? String
            else -> null
        }

        // Only flag when the argument is a string literal starting with a dot,
        // which strongly indicates the user is trying to check a file extension.
        if (argValue != null && argValue.startsWith(".")) {
            val receiver = node.receiver
            val receiverSource = receiver?.sourcePsi?.text ?: "file"
            val extensionWithoutDot = argValue.removePrefix(".")

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks path components, not string suffixes; " +
                        "`$receiverSource.endsWith(\"$argValue\")` will always be false. " +
                        "Did you mean `$receiverSource.path.endsWith(\"$argValue\")` or " +
                        "`$receiverSource.extension.equals(\"$extensionWithoutDot\")`?"
            )
        }
    }
}