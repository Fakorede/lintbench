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

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val transformationArg = arguments[0]
        val transformation = ConstantEvaluator.evaluate(context, transformationArg) as? String ?: return

        val uppercase = transformation.uppercase(Locale.US)
        val parts = uppercase.split("/")
        val algorithm = parts[0]

        if (parts.size == 1) {
            if (isBlockCipher(algorithm)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(transformationArg),
                    "`Cipher.getInstance` should not be called without setting the cipher mode"
                )
            }
        } else if (parts.size >= 2) {
            val mode = parts[1]
            if (mode == "ECB" && algorithm != "RSA") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(transformationArg),
                    "ECB mode should not be used"
                )
            }
        }
    }

    private fun isBlockCipher(algorithm: String): Boolean {
        return algorithm == "AES" ||
                algorithm == "DES" ||
                algorithm == "DESEDE" ||
                algorithm == "BLOWFISH" ||
                algorithm == "RC2"
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
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
            moreInfo = "https://goo.gle/GetInstance",
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}