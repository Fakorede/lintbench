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
            if (node.isConstructor) {
                val constructor = node.resolve() ?: return
                if (context.evaluator.isMemberInClass(constructor, SECURE_RANDOM)) {
                    val params = constructor.parameterList.parameters
                    if (params.size == 1 && params[0].type.canonicalText == "byte[]") {
                        report(context, node)
                    }
                }
            } else {
                val method = node.resolve() ?: return
                if (SET_SEED == method.name &&
                    context.evaluator.isMemberInSubClassOf(method, SECURE_RANDOM, false)
                ) {
                    report(context, node)
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using a fixed seed with `SecureRandom` can produce a predictable sequence of numbers"
        )
    }

    companion object {
        private const val SECURE_RANDOM = "java.security.SecureRandom"
        private const val SET_SEED = "setSeed"

        @JvmField
        val ISSUE = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable
                sequence of numbers. This may be useful for testing but it is not
                appropriate for secure use.
            """.trimIndent(),
            moreInfo = "https://goo.gle/SecureRandom",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}