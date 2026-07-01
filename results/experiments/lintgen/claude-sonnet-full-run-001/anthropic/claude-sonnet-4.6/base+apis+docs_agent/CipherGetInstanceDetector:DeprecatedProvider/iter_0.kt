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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val DEPRECATED_PROVIDER = Issue.create(
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

        private val CLASSES_WITH_GET_INSTANCE = setOf(
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

        private const val GET_INSTANCE = "getInstance"

        private val DEPRECATED_PROVIDERS = setOf("BC", "Crypto")
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass !in CLASSES_WITH_GET_INSTANCE) return

        val arguments = node.valueArguments
        // getInstance(String algorithm, String provider) or
        // getInstance(String algorithm, Provider provider)
        if (arguments.size < 2) return

        val providerArg = arguments[1]
        if (providerArg is ULiteralExpression) {
            val providerValue = providerArg.evaluateString() ?: return
            if (providerValue in DEPRECATED_PROVIDERS) {
                context.report(
                    DEPRECATED_PROVIDER,
                    node,
                    context.getLocation(providerArg),
                    "The `$providerValue` provider is deprecated and will not be provided " +
                        "when `targetSdkVersion` is P or higher"
                )
            }
        } else {
            // Try to evaluate constant expressions
            val evaluated = providerArg.evaluateString()
            if (evaluated != null && evaluated in DEPRECATED_PROVIDERS) {
                context.report(
                    DEPRECATED_PROVIDER,
                    node,
                    context.getLocation(providerArg),
                    "The `$evaluated` provider is deprecated and will not be provided " +
                        "when `targetSdkVersion` is P or higher"
                )
            }
        }
    }
}