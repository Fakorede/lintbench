package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
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
        // Check that the method is called on a java.io.File receiver
        val receiverType = node.receiverType ?: return
        if (receiverType.canonicalText != "java.io.File") return

        // Check that this is the Kotlin extension or the File.endsWith(File/String) variant
        // We want to flag calls where the argument starts with "." (looks like an extension)
        val arguments = node.valueArguments
        if (arguments.size != 1) return

        val arg = arguments[0]
        val argValue = arg.evaluate()

        // If we can statically evaluate the argument and it starts with ".", warn
        if (argValue is String && argValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks whole path components, not string suffixes; " +
                    "`file.endsWith(\".txt\")` will always return false. " +
                    "Did you mean `file.path.endsWith(\"${argValue}\")` or " +
                    "`file.extension.equals(\"${argValue.removePrefix(".")}\")`?"
            )
            return
        }

        // If the argument is a string literal (even if we couldn't evaluate it), check its text
        val sourcePsi = arg.sourcePsi
        if (sourcePsi != null) {
            val text = sourcePsi.text.trim('"', '\'')
            if (text.startsWith(".")) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "File.endsWith checks whole path components, not string suffixes; " +
                        "`file.endsWith(\"$text\")` will always return false. " +
                        "Did you mean `file.path.endsWith(\"$text\")` or " +
                        "`file.extension.equals(\"${text.removePrefix(".")}\")`?"
                )
                return
            }
        }

        // Also warn in general when endsWith is called on a File with a String argument,
        // since the intent is often to check a file extension suffix
        val containingClass = method.containingClass
        val declaringClass = containingClass?.qualifiedName

        // The Kotlin stdlib endsWith for File takes a File parameter, not String.
        // If the argument type is String, this is likely a mistake.
        val argType = arg.getExpressionType()?.canonicalText
        if (argType == "java.lang.String" || argType == "String") {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "File.endsWith checks whole path components, not string suffixes. " +
                    "Did you mean `file.path.endsWith(...)` or `file.extension.equals(...)`?"
            )
        }
    }
}