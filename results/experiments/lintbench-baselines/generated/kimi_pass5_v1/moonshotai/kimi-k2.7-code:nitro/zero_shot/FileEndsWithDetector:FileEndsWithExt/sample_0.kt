package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UastCallKind

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler<UCallExpression> {
        return object : UElementHandler<UCallExpression>() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.METHOD_CALL) return
                if (node.methodName != "endsWith") return
                if (node.valueArgumentCount != 1) return

                val receiver = node.receiver ?: return
                val receiverType = receiver.getExpressionType() ?: return
                val receiverClass = context.evaluator.getTypeClass(receiverType)
                if (receiverClass?.qualifiedName != "java.io.File") return

                val argument = node.valueArguments.firstOrNull() ?: return
                val argumentType = argument.getExpressionType() ?: return
                val argumentClass = context.evaluator.getTypeClass(argumentType)
                if (argumentClass?.qualifiedName != "java.lang.String") return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "File.endsWith checks whole path components, not string suffixes. " +
                            "Did you mean file.path.endsWith(...) or file.extension == ...?"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components,
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will
                return false. Instead you might have intended `file.path.endsWith` or
                `file.extension.equals`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}