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

class SecureRandomDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val SECURE_RANDOM_CLASS = "java.security.SecureRandom"
        private const val RANDOM_CLASS = "java.util.Random"

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

        private const val SET_SEED_METHOD = "setSeed"
        private const val CONSTRUCTOR_NAME = "SecureRandom"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(SET_SEED_METHOD, CONSTRUCTOR_NAME)
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        when (method.name) {
            SET_SEED_METHOD -> {
                if (!evaluator.isMemberInSubClassOf(method, SECURE_RANDOM_CLASS) &&
                    !evaluator.isMemberInSubClassOf(method, RANDOM_CLASS)
                ) {
                    return
                }
                // Check that the containing class is SecureRandom (not just Random)
                val containingClass = method.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName ?: return
                if (qualifiedName != SECURE_RANDOM_CLASS &&
                    !evaluator.extendsClass(containingClass, SECURE_RANDOM_CLASS, false)
                ) {
                    return
                }

                val arguments = node.valueArguments
                if (arguments.isEmpty()) return

                val seedArg = arguments.first()
                // Check if the seed is a constant/literal value (fixed seed)
                val constantValue = seedArg.evaluate()
                if (constantValue != null) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "It is dangerous to seed `SecureRandom` with the current time because " +
                            "that value is more predictable to an attacker than the default seed"
                    )
                } else {
                    // Still report if it looks like a fixed seed call
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
                            "it is not secure. Use `getSeed()`."
                    )
                }
            }

            CONSTRUCTOR_NAME -> {
                val containingClass = method.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName ?: return
                if (qualifiedName != SECURE_RANDOM_CLASS &&
                    !evaluator.extendsClass(containingClass, SECURE_RANDOM_CLASS, false)
                ) {
                    return
                }

                val arguments = node.valueArguments
                if (arguments.isEmpty()) return

                // SecureRandom(byte[] seed) constructor with a fixed/constant seed
                val seedArg = arguments.first()
                val constantValue = seedArg.evaluate()
                if (constantValue != null) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "It is dangerous to seed `SecureRandom` with a fixed seed: " +
                            "the instance will return a predictable sequence of numbers."
                    )
                }
            }
        }
    }
}