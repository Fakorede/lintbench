package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {
    companion object {
        private val CRYPTO_CLASSES = setOf(
            "java.security.MessageDigest",
            "java.security.Signature",
            "java.security.KeyPairGenerator",
            "java.security.KeyFactory",
            "java.security.AlgorithmParameters",
            "java.security.AlgorithmParameterGenerator",
            "java.security.KeyStore",
            "java.security.SecureRandom",
            "java.security.CertificateFactory",
            "java.security.CertPathBuilder",
            "java.security.CertPathValidator",
            "javax.crypto.Cipher",
            "javax.crypto.Mac",
            "javax.crypto.KeyGenerator",
            "javax.crypto.SecretKeyFactory",
            "javax.crypto.KeyAgreement",
            "javax.crypto.ExemptionMechanism",
            "javax.net.ssl.SSLContext",
            "javax.net.ssl.TrustManagerFactory",
            "javax.net.ssl.KeyManagerFactory"
        )

        @JvmField
        val DEPRECATED_PROVIDER = Issue.create(
            "DeprecatedProvider",
            "Using BC Provider",
            "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return
        val qualifiedName = evaluator.getQualifiedName(containingClass) ?: return

        if (qualifiedName !in CRYPTO_CLASSES) return

        val args = node.valueArguments
        if (args.size < 2) return

        val providerArg = args[1]
        if (providerArg.evaluateString() == "BC") {
            context.report(
                DEPRECATED_PROVIDER,
                context.getLocation(providerArg),
                "Using the `BC` provider is deprecated and will be removed in future Android versions."
            )
        }
    }
}