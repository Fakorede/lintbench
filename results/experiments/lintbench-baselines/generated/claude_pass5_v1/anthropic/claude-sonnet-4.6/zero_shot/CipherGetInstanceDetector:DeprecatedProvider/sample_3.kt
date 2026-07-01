package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val JAVAX_CRYPTO_CIPHER = "javax.crypto.Cipher"
        private const val JAVAX_CRYPTO_KEY_AGREEMENT = "javax.crypto.KeyAgreement"
        private const val JAVAX_CRYPTO_KEY_GENERATOR = "javax.crypto.KeyGenerator"
        private const val JAVAX_CRYPTO_MAC = "javax.crypto.Mac"
        private const val JAVAX_CRYPTO_SECRET_KEY_FACTORY = "javax.crypto.SecretKeyFactory"
        private const val JAVA_SECURITY_ALGO_PARAM_GENERATOR = "java.security.AlgorithmParameterGenerator"
        private const val JAVA_SECURITY_ALGO_PARAMS = "java.security.AlgorithmParameters"
        private const val JAVA_SECURITY_KEY_FACTORY = "java.security.KeyFactory"
        private const val JAVA_SECURITY_KEY_PAIR_GENERATOR = "java.security.KeyPairGenerator"
        private const val JAVA_SECURITY_MESSAGE_DIGEST = "java.security.MessageDigest"
        private const val JAVA_SECURITY_SECURE_RANDOM = "java.security.SecureRandom"
        private const val JAVA_SECURITY_SIGNATURE = "java.security.Signature"
        private const val JAVA_SECURITY_CERT_CERT_FACTORY = "java.security.cert.CertificateFactory"
        private const val JAVA_SECURITY_CERT_CERT_PATH_BUILDER = "java.security.cert.CertPathBuilder"
        private const val JAVA_SECURITY_CERT_CERT_PATH_VALIDATOR = "java.security.cert.CertPathValidator"
        private const val JAVA_SECURITY_CERT_CERT_STORE = "java.security.cert.CertStore"

        private val CRYPTO_CLASSES = setOf(
            JAVAX_CRYPTO_CIPHER,
            JAVAX_CRYPTO_KEY_AGREEMENT,
            JAVAX_CRYPTO_KEY_GENERATOR,
            JAVAX_CRYPTO_MAC,
            JAVAX_CRYPTO_SECRET_KEY_FACTORY,
            JAVA_SECURITY_ALGO_PARAM_GENERATOR,
            JAVA_SECURITY_ALGO_PARAMS,
            JAVA_SECURITY_KEY_FACTORY,
            JAVA_SECURITY_KEY_PAIR_GENERATOR,
            JAVA_SECURITY_MESSAGE_DIGEST,
            JAVA_SECURITY_SECURE_RANDOM,
            JAVA_SECURITY_SIGNATURE,
            JAVA_SECURITY_CERT_CERT_FACTORY,
            JAVA_SECURITY_CERT_CERT_PATH_BUILDER,
            JAVA_SECURITY_CERT_CERT_PATH_VALIDATOR,
            JAVA_SECURITY_CERT_CERT_STORE
        )

        private const val GET_INSTANCE = "getInstance"

        // Deprecated/removed BC provider names
        private val DEPRECATED_PROVIDERS = setOf("BC", "BC-FIPS", "BCFIPS", "Bouncy Castle")

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when \
                `targetSdkVersion` is P or higher.
            """,
            moreInfo = "https://goo.gle/DeprecatedProvider",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName !in CRYPTO_CLASSES) {
            return
        }

        val arguments = node.valueArguments
        // getInstance can be called with:
        //   getInstance(String algorithm)
        //   getInstance(String algorithm, String provider)
        //   getInstance(String algorithm, Provider provider)
        if (arguments.size < 2) {
            return
        }

        val providerArg: UExpression = arguments[1]
        val providerValue = providerArg.evaluateString() ?: return

        if (isDeprecatedProvider(providerValue)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(providerArg),
                "The `BC` provider is deprecated and when `targetSdkVersion` is P or " +
                        "higher, security `Provider`s that rely on it will not be available. " +
                        "See https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html " +
                        "for more details."
            )
        }
    }

    private fun isDeprecatedProvider(provider: String): Boolean {
        val trimmed = provider.trim()
        return DEPRECATED_PROVIDERS.any { it.equals(trimmed, ignoreCase = true) }
    }
}