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
        private val DEPRECATED_PROVIDERS = setOf("BC", "BOUNCY CASTLE")

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

        @JvmField
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

        if (qualifiedName !in CLASSES_WITH_GET_INSTANCE) {
            return
        }

        val arguments = node.valueArguments
        // We're looking for calls with a provider argument (2nd or 3rd argument)
        // getInstance(String algorithm, String provider)
        // getInstance(String algorithm, Provider provider)
        if (arguments.size < 2) {
            return
        }

        // The provider is the last argument
        val providerArg = arguments[arguments.size - 1]

        if (providerArg is ULiteralExpression) {
            val providerValue = providerArg.evaluateString() ?: return
            if (providerValue.uppercase() in DEPRECATED_PROVIDERS) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(providerArg),
                    "The `BC` provider is deprecated and when `targetSdkVersion` is P or " +
                            "higher, this method call will throw a `NoSuchAlgorithmException`"
                )
            }
        } else {
            // Try to evaluate the expression
            val evaluated = providerArg.evaluate()
            if (evaluated is String) {
                if (evaluated.uppercase() in DEPRECATED_PROVIDERS) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(providerArg),
                        "The `BC` provider is deprecated and when `targetSdkVersion` is P or " +
                                "higher, this method call will throw a `NoSuchAlgorithmException`"
                    )
                }
            }
        }
    }
}