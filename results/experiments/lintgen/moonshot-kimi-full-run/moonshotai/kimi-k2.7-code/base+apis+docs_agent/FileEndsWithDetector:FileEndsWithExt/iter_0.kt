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
import org.jetbrains.uast.UElement

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement?>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "endsWith") return

                val receiverType = node.receiverType ?: return
                val receiverClass = context.evaluator.typeClass(receiverType)?.qualifiedName
                if (receiverClass != "java.io.File") return

                val args = node.valueArguments
                if (args.size != 1) return

                val argType = args[0].getExpressionType() ?: return
                val argClass = context.evaluator.typeClass(argType)?.qualifiedName
                if (argClass != "java.lang.String") return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "File.endsWith(suffix) checks whole path components, not string suffixes. " +
                            "Use file.path.endsWith(...) or file.extension.equals(...) instead."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File.endsWith checks path components, not string suffixes",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. For example, `File("foo.txt").endsWith(".txt")` returns \
                false. You probably intended `file.path.endsWith(...)` or \
                `file.extension.equals(...)`.
                """,
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