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
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
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

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Check if the receiver is a java.io.File
        val receiverType = node.receiverType ?: return
        if (receiverType.canonicalText != "java.io.File") return

        // Check if the argument looks like a file extension (starts with '.')
        val arguments = node.valueArguments
        if (arguments.size != 1) return

        val arg = arguments[0]
        val argValue = arg.evaluate()
        if (argValue is String && argValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks whole path components, not string suffixes; " +
                        "`\"${argValue}\"` will never match. Did you mean " +
                        "`file.path.endsWith(\"${argValue}\")` or " +
                        "`file.extension.equals(\"${argValue.substring(1)}\")`?"
            )
        }
    }
}