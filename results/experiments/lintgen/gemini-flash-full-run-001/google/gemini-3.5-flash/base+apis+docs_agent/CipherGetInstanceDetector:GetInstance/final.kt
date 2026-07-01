package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.Locale

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val value = ConstantEvaluator.evaluate(context, argument) as? String ?: return

        val parts = value.split("/")
        if (parts.isEmpty()) return
        val algorithm = parts[0]

        if (parts.size == 1) {
            if (isSymmetric(algorithm)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(argument),
                    "Cipher.getInstance should not be called without setting the cipher mode and padding"
                )
            }
        } else if (parts.size > 1) {
            val mode = parts[1]
            if (mode.equals("ECB", ignoreCase = true)) {
                if (!algorithm.equals("RSA", ignoreCase = true)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(argument),
                        "ECB should not be used as the cipher mode"
                    )
                }
            }
        }
    }

    private fun isSymmetric(algorithm: String): Boolean {
        val upper = algorithm.uppercase(Locale.US)
        return upper == "AES" ||
                upper == "DES" ||
                upper == "DESEDE" ||
                upper == "BLOWFISH" ||
                upper == "RC2" ||
                upper == "RC4" ||
                upper == "RC5" ||
                upper == "ARCFOUR" ||
                upper == "CHACHA20"
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.
                """.trimIndent(),
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}