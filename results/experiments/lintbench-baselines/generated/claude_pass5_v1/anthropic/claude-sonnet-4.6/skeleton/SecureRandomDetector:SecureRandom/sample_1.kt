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
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.util.isConstructorCall

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            SecureRandomDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
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
            implementation = IMPLEMENTATION,
            moreInfo = "https://goo.gle/SecureRandom",
        )

        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val SET_SEED_METHOD = "setSeed"
        private const val NEXT_BYTES_METHOD = "nextBytes"

        private val APPLICABLE_METHODS = listOf(SET_SEED_METHOD, NEXT_BYTES_METHOD)
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, SECURE_RANDOM_CLASS, false)) {
            return
        }

        val methodName = method.name

        if (methodName == SET_SEED_METHOD) {
            // setSeed(long) or setSeed(byte[]) with a fixed/literal seed is problematic
            val arguments = node.valueArguments
            if (arguments.isNotEmpty()) {
                val seedArg = arguments[0]
                if (isFixedSeed(seedArg)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                            "it is not secure. Use `getSeed()`.",
                    )
                }
            }
        } else if (methodName == NEXT_BYTES_METHOD) {
            // Check if the SecureRandom was constructed with a fixed seed
            // i.e., new SecureRandom(fixedSeedBytes)
            val receiver = node.receiver ?: return
            checkReceiverForFixedSeed(context, node, receiver)
        }
    }

    /**
     * Checks whether the receiver expression refers to a SecureRandom constructed
     * with a fixed seed.
     */
    private fun checkReceiverForFixedSeed(
        context: JavaContext,
        node: UCallExpression,
        receiver: UExpression,
    ) {
        // We look for patterns like: new SecureRandom(seed).nextBytes(...)
        // or variable references where the variable was assigned new SecureRandom(seed)
        if (receiver is UCallExpression && receiver.isConstructorCall()) {
            val constructorClass = receiver.classReference?.resolvedName ?: return
            if (constructorClass == "SecureRandom") {
                val args = receiver.valueArguments
                if (args.isNotEmpty() && isFixedSeed(args[0])) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not use a fixed seed with `SecureRandom`.",
                    )
                }
            }
        }
    }

    /**
     * Determines if the given expression represents a fixed/constant seed value.
     * A fixed seed is a literal value (number, string, etc.) or a constant reference.
     */
    private fun isFixedSeed(expression: UExpression): Boolean {
        return when (expression) {
            is ULiteralExpression -> {
                // Any literal value (number, string, etc.) is a fixed seed
                expression.value != null
            }
            is UReferenceExpression -> {
                // Check if it resolves to a compile-time constant
                val resolved = expression.resolve()
                if (resolved is com.intellij.psi.PsiField) {
                    resolved.hasModifierProperty(com.intellij.psi.PsiModifier.FINAL) &&
                        resolved.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC) &&
                        resolved.computeConstantValue() != null
                } else if (resolved is com.intellij.psi.PsiLocalVariable) {
                    resolved.computeConstantValue() != null
                } else {
                    false
                }
            }
            else -> false
        }
    }
}