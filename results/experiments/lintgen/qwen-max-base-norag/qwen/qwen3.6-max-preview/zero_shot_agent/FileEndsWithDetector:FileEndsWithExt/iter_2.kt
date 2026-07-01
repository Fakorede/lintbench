package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getExpressionType

class FileEndsWithDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UCallExpression::class.java)

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        if (node.methodName != "endsWith") return

        val receiver = node.receiver ?: return
        val receiverType = receiver.getExpressionType() ?: return

        if (context.evaluator.typeMatches(receiverType, "java.io.File")) {
            context.report(
                ISSUE,
                node,
                context.getNameLocation(node),
                "File.endsWith checks whole path components, not string suffixes. " +
                        "Use file.path.endsWith or file.extension.equals instead."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}