package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val CIPHER_CLASSES = setOf(
            "javax.crypto.Cipher",
            "javax.crypto.KeyAgreement",
            "javax.crypto.KeyGenerator",
            "javax.crypto.Mac",
            "javax.crypto.SecretKeyFactory",
            "java.security.AlgorithmParameterGenerator",
            "java.security.AlgorithmParameters",
            "java.security.CertPathBuilder",
            "java.security.CertPathValidator",
            "java.security.CertStore",
            "java.security.KeyFactory",
            "java.security.KeyPairGenerator",
            "java.security.KeyStore",
            "java.security.MessageDigest",
            "java.security.SecureRandom",
            "java.security.Signature",
            "java.security.cert.CertificateFactory"
        )

        private val DEPRECATED_PROVIDERS = setOf("BC", "Crypto")

        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when \
                `targetSdkVersion` is P or higher.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            moreInfo = "https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html",
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).addMoreInfo("https://goo.gle/DeprecatedProvider")

        private const val GET_INSTANCE = "getInstance"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (!CIPHER_CLASSES.contains(qualifiedName)) {
            return
        }

        val arguments = node.valueArguments
        // getInstance(String algorithm, String provider) or
        // getInstance(String algorithm, Provider provider)
        if (arguments.size < 2) {
            return
        }

        val providerArg = arguments[1]

        // Only flag string literal providers
        if (providerArg is ULiteralExpression) {
            val providerValue = providerArg.evaluateString() ?: return
            if (DEPRECATED_PROVIDERS.contains(providerValue)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(providerArg),
                    "The `$providerValue` provider is deprecated and will not be supported " +
                            "when `targetSdkVersion` is P or higher"
                )
            }
        } else {
            // Try to evaluate constant expressions
            val evaluated = providerArg.evaluateString()
            if (evaluated != null && DEPRECATED_PROVIDERS.contains(evaluated)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(providerArg),
                    "The `$evaluated` provider is deprecated and will not be supported " +
                            "when `targetSdkVersion` is P or higher"
                )
            }
        }
    }
}