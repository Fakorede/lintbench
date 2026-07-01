package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

class SecureRandomDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve() ?: return
            val evaluator = context.evaluator

            val isFixedSeed = when {
                node.isConstructor -> {
                    val containingClass = method.containingClass ?: return
                    evaluator.extendsClass(containingClass, "java.security.SecureRandom", false)
                            && node.valueArgumentCount > 0
                }
                else -> {
                    evaluator.isMemberInClass(method, "java.security.SecureRandom")
                            && method.name == "setSeed"
                            && node.valueArgumentCount > 0
                }
            }

            if (!isFixedSeed) {
                return
            }

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using a fixed seed with `SecureRandom` can produce a predictable sequence of random numbers"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause `SecureRandom` to return a predictable
                sequence of numbers. This may be useful for testing but it is not appropriate
                for secure use.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}