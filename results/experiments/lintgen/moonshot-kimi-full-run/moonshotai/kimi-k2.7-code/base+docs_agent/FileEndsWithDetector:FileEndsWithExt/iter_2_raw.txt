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

class FileEndsWithDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "endsWith") return

                val method = node.resolve()
                if (method != null) {
                    val containingClass = method.containingClass?.qualifiedName ?: return
                    if (!containingClass.startsWith("kotlin.io.FilesKt")) return

                    val receiverParam = method.parameterList.parameters.firstOrNull() ?: return
                    val receiverType = receiverParam.type
                    val receiverClass = context.evaluator.getTypeClass(receiverType) ?: return
                    if (receiverClass.qualifiedName == "java.io.File") {
                        report(node)
                    }
                    return
                }

                val receiver = node.receiver ?: return
                val receiverType = receiver.getExpressionType() ?: return
                val receiverClass = context.evaluator.getTypeClass(receiverType) ?: return
                if (receiverClass.qualifiedName == "java.io.File") {
                    report(node)
                }
            }

            private fun report(node: UCallExpression) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "File.endsWith checks whole path components, not string suffixes. " +
                        "Did you mean `file.path.endsWith(...)` or `file.extension == ...`?"
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
                The Kotlin extension method `File.endsWith(suffix)` checks whole path
                components, not just string suffixes. For example,
                `File("foo.txt").endsWith(".txt")` returns false. You probably want
                `file.path.endsWith(...)` or compare against `file.extension`.
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