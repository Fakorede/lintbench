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
import org.jetbrains.uast.UNewExpression
import org.jetbrains.uast.UReferenceExpression

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val SET_SEED_METHOD = "setSeed"

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
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/SecureRandom",
            androidSpecific = false,
        )

        private fun isFixedSeed(argument: UExpression): Boolean {
            // Literal values are always fixed seeds
            if (argument is ULiteralExpression) {
                return true
            }
            // If the argument is a reference (e.g., a constant or variable),
            // we conservatively check if it resolves to a compile-time constant
            if (argument is UReferenceExpression) {
                val resolved = argument.resolve()
                if (resolved is com.intellij.psi.PsiField && resolved.hasModifierProperty(com.intellij.psi.PsiModifier.FINAL)) {
                    return true
                }
                if (resolved is com.intellij.psi.PsiLocalVariable) {
                    // Could be a local variable; conservatively flag it if we can't tell
                    return false
                }
            }
            return false
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_SEED_METHOD)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        // Check if this is SecureRandom.setSeed(...)
        if (!evaluator.isMemberInClass(method, SECURE_RANDOM_CLASS) &&
            !evaluator.isMemberInSubClassOf(method, SECURE_RANDOM_CLASS)
        ) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return

        // Check if the seed argument is a fixed/literal value
        if (isFixedSeed(argument)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(argument),
                "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                    "it is not secure. Use `getSeed()`."
            )
            return
        }

        // Also check for new SecureRandom(seed) constructor calls with fixed seed
        // by checking if this setSeed is called immediately on a new instance
        // (handled separately via constructor check below)
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(SECURE_RANDOM_CLASS)
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        // SecureRandom(byte[] seed) constructor with a fixed seed is also problematic
        val argument = node.valueArguments.firstOrNull() ?: return

        if (isFixedSeed(argument)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(argument),
                "Do not call `new SecureRandom(seed)` with a fixed seed: " +
                    "it is not secure. Use `new SecureRandom()` instead."
            )
        }
    }
}