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
import org.jetbrains.uast.UastCallKind

class SecureRandomDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.valueArgumentCount == 0) return

                val method = node.resolve() ?: return

                if (node.kind == UastCallKind.CONSTRUCTOR_CALL) {
                    val containingClass = method.containingClass?.qualifiedName ?: return
                    if (containingClass == "java.security.SecureRandom") {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Specifying a fixed seed with `SecureRandom` produces a predictable sequence of numbers"
                        )
                    }
                    return
                }

                if (node.methodName == "setSeed") {
                    val receiverType = node.receiverType ?: return
                    if (context.evaluator.typeMatches(receiverType, "java.security.SecureRandom")) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Calling `setSeed` on a `SecureRandom` instance can make its output predictable"
                        )
                    }
                }
            }
        }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not \
                appropriate for secure use.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/SecureRandom"
        )
    }
}