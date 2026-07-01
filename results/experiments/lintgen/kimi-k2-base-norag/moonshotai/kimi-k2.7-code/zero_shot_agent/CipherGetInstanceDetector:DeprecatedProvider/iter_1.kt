package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableCallNames(): List<String> = listOf(GET_INSTANCE, GET_PROVIDER)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        if (context.project.targetSdk < ANDROID_P) {
            return
        }

        val method = node.resolve() ?: return
        val className = method.containingClass?.qualifiedName ?: return
        val methodName = node.methodName ?: return

        when {
            className == SECURITY && methodName == GET_PROVIDER -> {
                if (node.valueArguments.firstOrNull()?.evaluateString() == BC) {
                    report(context, node)
                }
            }
            className in CRYPTO_CLASSES && methodName == GET_INSTANCE -> {
                for (arg in node.valueArguments.drop(1)) {
                    if (arg.evaluateString() == BC) {
                        report(context, node)
                        return
                    }
                }
            }
        }
    }

    private fun report(context: JavaContext, node: UCallExpression) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "The `BC` provider has been deprecated and will not be provided when targetSdkVersion is P or higher."
        )
    }

    companion object {
        private const val BC = "BC"
        private const val GET_INSTANCE = "getInstance"
        private const val GET_PROVIDER = "getProvider"
        private const val ANDROID_P = 28
        private const val SECURITY = "java.security.Security"

        private val CRYPTO_CLASSES = setOf(
            "javax.crypto.Cipher",
            "javax.crypto.KeyGenerator",
            "javax.crypto.KeyAgreement",
            "javax.crypto.KeyPairGenerator",
            "javax.crypto.Mac",
            "javax.crypto.SecretKeyFactory",
            "java.security.MessageDigest",
            "java.security.Signature",
            "java.security.KeyStore",
            "java.security.AlgorithmParameters",
            "java.security.AlgorithmParameterGenerator",
            "java.security.KeyFactory",
            "java.security.SecureRandom",
            "javax.net.ssl.SSLContext",
            "javax.net.ssl.TrustManagerFactory",
            "javax.net.ssl.KeyManagerFactory",
            "java.security.cert.CertStore",
            "java.security.cert.CertPathValidator",
            "java.security.cert.CertPathBuilder",
            "java.security.cert.CertificateFactory"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` (BouncyCastle) provider has been deprecated on Android and is not
                available when `targetSdkVersion` is 28 (Android P) or higher. Calls that
                request it may throw `NoSuchAlgorithmException` or similar errors at runtime.
                Use the default provider or another supported provider instead.

                Reference documentation:
                - https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html
                - https://goo.gle/DeprecatedProvider
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}