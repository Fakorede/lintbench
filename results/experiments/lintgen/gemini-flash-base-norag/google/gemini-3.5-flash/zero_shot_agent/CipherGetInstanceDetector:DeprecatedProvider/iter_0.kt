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
                The `BC` provider has been deprecated and will not be provided when \
                `targetSdkVersion` is P or higher.
                """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html"
        )

        private val JCA_CLASSES = setOf(
            "javax.crypto.Cipher",
            "java.security.Signature",
            "javax.crypto.Mac",
            "javax.crypto.KeyAgreement",
            "java.security.KeyPairGenerator",
            "java.security.KeyFactory",
            "javax.crypto.SecretKeyFactory",
            "java.security.SecureRandom",
            "java.security.AlgorithmParameters",
            "java.security.AlgorithmParameterGenerator",
            "java.security.cert.CertificateFactory",
            "java.security.cert.CertPathBuilder",
            "java.security.cert.CertPathValidator",
            "java.security.cert.CertStore",
            "java.security.KeyStore",
            "javax.net.ssl.TrustManagerFactory",
            "javax.net.ssl.KeyManagerFactory",
            "javax.net.ssl.SSLContext"
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName !in JCA_CLASSES) {
            val isJca = JCA_CLASSES.any { evaluator.inheritsFrom(containingClass, it, false) }
            if (!isJca) return
        }

        if (node.valueArgumentCount == 2) {
            val providerArg = node.valueArguments[1]
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

    private fun isBcProvider(context: JavaContext, expression: UExpression): Boolean {
        val constant = ConstantEvaluator.evaluate(context, expression)
        if (constant is String && constant.equals("BC", ignoreCase = true)) {
            return true
        }

        if (expression is UCallExpression) {
            val method = expression.resolve()
            if (method != null && method.name == "getProvider") {
                val containingClass = method.containingClass
                if (containingClass != null && "java.security.Security" == containingClass.qualifiedName) {
                    if (expression.valueArgumentCount == 1) {
                        val arg = expression.valueArguments[0]
                        val argConstant = ConstantEvaluator.evaluate(context, arg)
                        if (argConstant is String && argConstant.equals("BC", ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }
}