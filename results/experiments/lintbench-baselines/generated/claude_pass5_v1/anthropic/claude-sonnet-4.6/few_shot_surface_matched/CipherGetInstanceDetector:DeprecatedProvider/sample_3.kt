package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.minSdkLessThan
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val BC_PROVIDER = "BC"
        private const val ANDROID_OPEN_SSL_PROVIDER = "AndroidOpenSSL"
        private const val TARGET_API_P = 28

        private val APPLICABLE_METHODS = listOf("getInstance")

        private val CRYPTO_CLASSES = setOf(
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
            "java.security.cert.CertificateFactory",
            "java.security.cert.CertPathBuilder",
            "java.security.cert.CertPathValidator",
            "java.security.cert.CertStore",
        )

        @JvmField
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
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html"
        ).addMoreInfo("https://goo.gle/DeprecatedProvider")
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName !in CRYPTO_CLASSES) {
            return
        }

        val args = node.valueArguments
        // We need at least 2 arguments: algorithm and provider
        if (args.size < 2) {
            return
        }

        val providerArg = args[1]

        // Only flag when the provider argument is a string literal equal to "BC"
        if (providerArg !is ULiteralExpression) {
            return
        }

        val providerValue = providerArg.evaluateString() ?: return
        if (providerValue != BC_PROVIDER) {
            return
        }

        val location = context.getLocation(providerArg)
        val message = "The `BC` provider is deprecated and when `targetSdkVersion` is P or " +
                "higher, security `getInstance()` calls with `BC` provider will throw a " +
                "`NoSuchAlgorithmException`. See " +
                "https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html " +
                "for more details."

        val incident = Incident(ISSUE, node, location, message)
        context.report(incident)
    }

    override fun filterIncident(context: Context, incident: Incident, map: com.android.tools.lint.detector.api.LintMap): Boolean {
        if (context.mainProject.isAndroidProject) {
            map.put("minSdk", context.mainProject.minSdk)
        }
        return true
    }
}