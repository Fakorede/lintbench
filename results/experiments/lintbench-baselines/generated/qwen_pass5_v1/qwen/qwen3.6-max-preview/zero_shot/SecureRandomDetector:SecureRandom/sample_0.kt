package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UArrayLiteralExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind

class SecureRandomDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        val method = node.resolve() ?: return
        val cls = method.containingClass ?: return
        if (context.evaluator.getQualifiedName(cls) != "java.security.SecureRandom") return

        val seedArg: UExpression? = when {
            node.kind == UastCallKind.CONSTRUCTOR_CALL && node.valueArguments.size == 1 -> node.valueArguments[0]
            method.name == "setSeed" && node.valueArguments.size == 1 -> node.valueArguments[0]
            else -> null
        }

        if (seedArg != null && isFixedSeed(seedArg)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using a fixed seed with `SecureRandom`"
            )
        }
    }

    private fun isFixedSeed(arg: UExpression): Boolean {
        if (arg.evaluate() != null) return true

        if (arg is UCallExpression) {
            val m = arg.resolve() ?: return false
            if ((m.name == "getBytes" || m.name == "toByteArray") && arg.receiver?.evaluate() != null) {
                return true
            }
        }

        if (arg is UArrayLiteralExpression) {
            return arg.expressions.all { it.evaluate() != null }
        }

        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).setMoreInfo("https://goo.gle/SecureRandom")
    }
}