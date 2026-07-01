package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString

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
        // Check that the receiver is a java.io.File
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "java.io.File") &&
            !evaluator.isMemberInSubClassOf(method, "java.io.File", false)
        ) {
            return
        }

        // The method should take a single argument
        val arguments = node.valueArguments
        if (arguments.size != 1) return

        val arg: UExpression = arguments[0]

        // Check if the argument is a string literal starting with a dot (file extension pattern)
        val value = arg.evaluateString()
        if (value != null && value.startsWith(".")) {
            val message = "`File.endsWith` checks whole path components, not string suffixes; " +
                    "`file.endsWith(\"${value}\")` will always return false. " +
                    "Did you mean `file.path.endsWith(\"${value}\")` or " +
                    "`file.extension.equals(\"${value.removePrefix(".")}\")`?"
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message
            )
        } else if (value == null) {
            // Even if we can't evaluate the string, if the argument type is String,
            // we should warn since File.endsWith(File) is the non-extension version
            // and File.endsWith(String) is the Kotlin extension which checks path components.
            val argType = arg.getExpressionType()
            if (argType != null && argType.canonicalText == "java.lang.String") {
                val message = "`File.endsWith` checks whole path components, not string suffixes. " +
                        "Did you mean `file.path.endsWith(...)` or `file.extension.equals(...)`?"
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }
    }
}