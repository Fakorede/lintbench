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
import org.jetbrains.uast.UExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass == "javax.crypto.Cipher" ||
            containingClass == "java.security.Signature" ||
            containingClass == "java.security.KeyPairGenerator" ||
            containingClass == "java.security.KeyFactory" ||
            containingClass == "javax.crypto.Mac" ||
            containingClass == "javax.crypto.KeyAgreement" ||
            containingClass == "java.security.MessageDigest" ||
            containingClass == "javax.crypto.SecretKeyFactory" ||
            containingClass == "java.security.SecureRandom" ||
            containingClass == "java.security.cert.CertificateFactory" ||
            containingClass == "java.security.KeyStore" ||
            containingClass == "javax.crypto.KeyGenerator"
        ) {
            val args = node.valueArguments
            if (args.size >= 2) {
                val providerArg = args[1]
                if (isBcProvider(context, providerArg)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(providerArg),
                        "The `BC` provider is deprecated and should not be used"
                    )
                }
            }
        }
    }

    private fun isBcProvider(context: JavaContext, expression: UExpression): Boolean {
        val constant = ConstantEvaluator.evaluate(context, expression)
        if (constant == "BC") {
            return true
        }
        if (expression is UCallExpression) {
            val method = expression.resolve()
            if (method != null && method.name == "getProvider") {
                val containingClass = method.containingClass?.qualifiedName
                if (containingClass == "java.security.Security") {
                    val args = expression.valueArguments
                    if (args.isNotEmpty()) {
                        val firstArg = ConstantEvaluator.evaluate(context, args[0])
                        if (firstArg == "BC") {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
}