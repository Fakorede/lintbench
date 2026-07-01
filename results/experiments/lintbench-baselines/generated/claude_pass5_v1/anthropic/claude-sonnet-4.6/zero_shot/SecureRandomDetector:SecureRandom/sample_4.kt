package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.evaluateString

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val SET_SEED_METHOD = "setSeed"
        private const val RANDOM_CLASS = "java.util.Random"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "SecureRandom",
            briefDescription = "Using a fixed seed with `SecureRandom`",
            explanation = """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            moreInfo = "https://goo.gle/SecureRandom",
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val SEED_CONSTRUCTOR_SIGNATURES = listOf(
            "SecureRandom(byte[])"
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_SEED_METHOD, "SecureRandom")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = node.methodName ?: return
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        when (methodName) {
            SET_SEED_METHOD -> {
                if (qualifiedName == SECURE_RANDOM_CLASS ||
                    isSubclassOf(context, containingClass, SECURE_RANDOM_CLASS)
                ) {
                    val arguments = node.valueArguments
                    if (arguments.isNotEmpty()) {
                        val seedArg = arguments[0]
                        if (isFixedSeed(seedArg)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                                        "it is not secure. Use `getSeed()`."
                            )
                        }
                    }
                }
            }
            "SecureRandom" -> {
                // Constructor call with a seed argument
                if (qualifiedName == SECURE_RANDOM_CLASS) {
                    val arguments = node.valueArguments
                    if (arguments.isNotEmpty()) {
                        val seedArg = arguments[0]
                        if (isFixedSeed(seedArg)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Do not call `new SecureRandom(seed)` with a fixed seed: " +
                                        "it is not secure. Use `new SecureRandom()`."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isFixedSeed(expression: UExpression): Boolean {
        return when (expression) {
            is ULiteralExpression -> {
                // Any literal value is a fixed seed
                expression.value != null
            }
            is UQualifiedReferenceExpression -> {
                // e.g., someObj.someField - could be a constant
                isConstantExpression(expression)
            }
            is USimpleNameReferenceExpression -> {
                isConstantExpression(expression)
            }
            else -> {
                // Try to evaluate as a constant
                val evaluated = expression.evaluate()
                evaluated != null
            }
        }
    }

    private fun isConstantExpression(expression: UExpression): Boolean {
        val evaluated = expression.evaluate()
        return evaluated != null
    }

    private fun isSubclassOf(
        context: JavaContext,
        cls: com.intellij.psi.PsiClass,
        superClassName: String
    ): Boolean {
        val evaluator = context.evaluator
        return evaluator.extendsClass(cls, superClassName, false)
    }
}