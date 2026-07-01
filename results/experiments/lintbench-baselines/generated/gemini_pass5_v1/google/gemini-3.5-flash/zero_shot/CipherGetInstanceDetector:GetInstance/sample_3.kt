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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        val firstArg = node.valueArguments.firstOrNull() ?: return
        val transformation = ConstantEvaluator.evaluate(context, firstArg) as? String ?: return

        val parts = transformation.split("/")
        if (parts.isEmpty()) return

        val algorithm = parts[0].uppercase()
        val isBlockCipher = algorithm == "AES" || 
                            algorithm == "DES" || 
                            algorithm == "DESEDE" || 
                            algorithm == "BLOWFISH" || 
                            algorithm == "RC2"

        if (isBlockCipher) {
            val hasNoMode = parts.size < 3
            val isEcb = parts.size > 1 && parts[1].equals("ECB", ignoreCase = true)

            if (hasNoMode || isEcb) {
                val message = "Cipher#getInstance should not be called with ECB as the cipher mode or " +
                        "without setting the cipher mode because the default mode on android is " +
                        "ECB, which is insecure."
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "GetInstance",
            briefDescription = "Cipher.getInstance with ECB",
            explanation = "Cipher#getInstance should not be called with ECB as the cipher mode or " +
                    "without setting the cipher mode because the default mode on android is " +
                    "ECB, which is insecure.",
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