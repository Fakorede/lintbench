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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val CRYPTO_CLASSES = setOf(
            "javax.crypto.Cipher",
            "java.security.MessageDigest",
            "java.security.Signature",
            "javax.crypto.Mac",
            "javax.crypto.KeyGenerator",
            "javax.crypto.KeyPairGenerator",
            "javax.crypto.SecretKeyFactory",
            "java.security.KeyFactory",
            "java.security.AlgorithmParameters",
            "java.security.AlgorithmParameterGenerator",
            "java.security.KeyStore",
            "java.security.cert.CertPathBuilder",
            "java.security.cert.CertPathValidator",
            "java.security.cert.CertificateFactory",
            "javax.net.ssl.SSLContext",
            "javax.net.ssl.KeyManagerFactory",
            "javax.net.ssl.TrustManagerFactory"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (qualifiedName !in CRYPTO_CLASSES) return

        for (arg in node.valueArguments) {
            if (context.evaluator.evaluate(arg) as? String == "BC") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using BC Provider"
                )
                return
            }
        }
    }
}