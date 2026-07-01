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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val className = method.containingClass?.qualifiedName ?: return
        if (className !in CRYPTO_CLASSES) return

        val args = node.valueArguments
        if (args.size < 2) return

        val provider = ConstantEvaluator.evaluate(context, args[1]) as? String ?: return
        if (provider == "BC") {
            context.report(
                ISSUE,
                node,
                context.getLocation(args[1]),
                "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher."
            )
        }
    }

    companion object {
        private val CRYPTO_CLASSES = setOf(
            "java.security.AlgorithmParameterGenerator",
            "java.security.AlgorithmParameters",
            "java.security.KeyFactory",
            "java.security.KeyPairGenerator",
            "java.security.KeyStore",
            "java.security.MessageDigest",
            "java.security.SecureRandom",
            "java.security.Signature",
            "java.security.cert.CertificateFactory",
            "java.security.cert.CertPathBuilder",
            "java.security.cert.CertPathValidator",
            "java.security.cert.CertStore",
            "javax.crypto.Cipher",
            "javax.crypto.KeyAgreement",
            "javax.crypto.KeyGenerator",
            "javax.crypto.Mac",
            "javax.crypto.SecretKeyFactory",
            "javax.net.ssl.KeyManagerFactory",
            "javax.net.ssl.SSLContext",
            "javax.net.ssl.TrustManagerFactory"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            "DeprecatedProvider",
            "Using BC Provider",
            """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                Use a different provider or rely on the default device provider.
                See https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html
                and https://goo.gle/DeprecatedProvider for more details.
            """.trimIndent(),
            Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            "https://goo.gle/DeprecatedProvider",
            Category.SECURITY,
            6,
            Severity.WARNING
        )
    }
}