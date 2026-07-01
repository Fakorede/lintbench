package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val DEPRECATED_PROVIDERS = setOf("BC", "BOUNCY CASTLE")

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
            "javax.net.ssl.SSLContext",
            "javax.net.ssl.TrustManagerFactory",
            "javax.net.ssl.KeyManagerFactory",
            "java.security.cert.CertificateFactory"
        )

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
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName !in CIPHER_CLASSES) return

        val arguments = node.valueArguments
        // getInstance with a provider argument: getInstance(String, String) or getInstance(String, Provider)
        if (arguments.size < 2) return

        val providerArg: UExpression = arguments[1]

        // Check if the second argument is a String (provider name)
        val providerType = providerArg.getExpressionType()
        val providerTypeName = providerType?.canonicalText ?: return

        // Only flag string-based provider arguments
        if (providerTypeName != "java.lang.String") return

        val providerValue = providerArg.evaluateString() ?: return

        if (providerValue.uppercase() in DEPRECATED_PROVIDERS) {
            context.report(
                ISSUE,
                node,
                context.getLocation(providerArg),
                "The `${providerValue}` provider is deprecated and will not be supported " +
                    "when `targetSdkVersion` is P or higher"
            )
        }
    }
}