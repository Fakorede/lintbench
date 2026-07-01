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
import org.jetbrains.uast.getExpressionType

class FileEndsWithDetector : Detector(), Detector.SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler<UCallExpression> {
        return object : UElementHandler<UCallExpression>() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "endsWith") return

                val receiver = node.receiver ?: return
                val receiverType = receiver.getExpressionType() ?: return
                val receiverClass = context.evaluator.getTypeClass(receiverType) ?: return
                if (receiverClass.qualifiedName != "java.io.File") return

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

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File.endsWith checks whole path components",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path \
                components, not just string suffixes. For example, \
                `File("foo.txt").endsWith(".txt")` returns false. You probably want \
                `file.path.endsWith(...)` or compare against `file.extension`.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}