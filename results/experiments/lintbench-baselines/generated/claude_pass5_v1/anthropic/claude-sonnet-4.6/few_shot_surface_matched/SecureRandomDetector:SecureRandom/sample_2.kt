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
        private const val SET_SEED_METHOD = "setSeed"
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
            moreInfo = "https://goo.gle/SecureRandom",
            implementation = Implementation(
                SecureRandomDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = false
        )

        private const val MESSAGE_SET_SEED =
            "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: it is not secure. " +
                "Use `getSeed()` to get a seed instead."

        private const val MESSAGE_CONSTRUCTOR_SEED =
            "Do not call `new SecureRandom(seed)` with a fixed seed: it is not secure. " +
                "Use `new SecureRandom()` instead."
    }

    override fun getApplicableMethodNames(): List<String> = listOf(SET_SEED_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        val isSecureRandom = evaluator.isMemberInClass(method, SECURE_RANDOM_CLASS)
            || evaluator.isMemberInSubClassOf(method, SECURE_RANDOM_CLASS)

        if (!isSecureRandom) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val seedArgument = arguments[0]
        val argumentType = seedArgument.getExpressionType()?.canonicalText

        // Check if the seed is a fixed/literal value rather than a dynamic one
        // We warn whenever setSeed is called with a fixed seed (literal or constant)
        val evaluatedValue = context.evaluator.let {
            try {
                seedArgument.evaluate()
            } catch (e: Exception) {
                null
            }
        }

        if (evaluatedValue != null) {
            // The seed evaluates to a constant/literal value — this is a fixed seed
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE_SET_SEED
            )
        } else if (argumentType != null) {
            // Check for common patterns like passing a fixed long/int literal or byte array literal
            val sourcePsi = seedArgument.sourcePsi
            if (sourcePsi != null) {
                val sourceText = sourcePsi.text?.trim() ?: return
                // If the source looks like a numeric literal or simple constant expression
                if (isLiteralOrSimpleConstant(sourceText)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        MESSAGE_SET_SEED
                    )
                }
            }
        }
    }

    private fun isLiteralOrSimpleConstant(text: String): Boolean {
        // Numeric literal (e.g., 12345L, 0, -1)
        val numericLiteral = Regex("^-?[0-9]+[lL]?$")
        if (numericLiteral.matches(text)) return true

        // Hex literal (e.g., 0xFF)
        val hexLiteral = Regex("^0[xX][0-9a-fA-F]+[lL]?$")
        if (hexLiteral.matches(text)) return true

        // String literal
        if (text.startsWith("\"") && text.endsWith("\"")) return true

        // Byte array literal (e.g., new byte[]{1,2,3})
        if (text.startsWith("new byte[") || text.startsWith("new byte[] ")) return true
        if (text.startsWith("{") && text.endsWith("}")) return true

        return false
    }
}