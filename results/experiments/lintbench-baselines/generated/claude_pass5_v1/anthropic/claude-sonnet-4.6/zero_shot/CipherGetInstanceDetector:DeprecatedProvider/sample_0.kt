package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val DEPRECATED_PROVIDERS = setOf("BC", "BOUNCY CASTLE", "BOUNCYCASTLE")

        private val CRYPTOGRAPHIC_CLASSES = setOf(
            "javax.crypto.Cipher",
            "javax.crypto.KeyAgreement",
            "javax.crypto.KeyGenerator",
            "javax.crypto.Mac",
            "javax.crypto.SecretKeyFactory",
            "java.security.AlgorithmParameterGenerator",
            "java.security.AlgorithmParameters",
            "java.security.KeyFactory",
            "java.security.KeyPairGenerator",
            "java.security.KeyStore",
            "java.security.MessageDigest",
            "java.security.SecureRandom",
            "java.security.Signature",
            "java.security.cert.CertPathBuilder",
            "java.security.cert.CertPathValidator",
            "java.security.cert.CertStore",
            "java.security.cert.CertificateFactory"
        )

        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when \
                `targetSdkVersion` is P or higher.
                """,
            moreInfo = "https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        ).addMoreInfo("https://goo.gle/DeprecatedProvider")
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName !in CRYPTOGRAPHIC_CLASSES) return

        val arguments = node.valueArguments
        // getInstance(String algorithm, String provider) or getInstance(String algorithm, Provider provider)
        if (arguments.size < 2) return

        val providerArg: UExpression = arguments[1]
        val providerValue = providerArg.evaluateString() ?: return

        if (providerValue.uppercase().replace(" ", "") in DEPRECATED_PROVIDERS.map { it.replace(" ", "") }) {
            context.report(
                ISSUE,
                node,
                context.getLocation(providerArg),
                "The `BC` provider is deprecated and when `targetSdkVersion` is P or " +
                        "higher, security `Provider`s that were provided by `BouncyCastle` " +
                        "are no longer available"
            )
        }
    }
}