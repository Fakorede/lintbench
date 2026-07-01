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
        private const val ANDROID_P_API_VERSION: Int = 28

        private val DEPRECATED_PROVIDERS = setOf("BC", "Crypto")

        private val GET_INSTANCE_CLASSES = setOf(
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
            "java.security.cert.CertificateFactory",
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

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName !in GET_INSTANCE_CLASSES) {
            return
        }

        // The provider is typically the last argument in a multi-argument getInstance call.
        // getInstance(String algorithm, String provider) or
        // getInstance(String algorithm, Provider provider)
        val args = node.valueArguments
        if (args.size < 2) return

        val providerArg = args.last()

        // Check if the provider argument is a string literal matching a deprecated provider
        if (providerArg is ULiteralExpression) {
            val providerValue = providerArg.evaluateString() ?: return
            if (providerValue in DEPRECATED_PROVIDERS) {
                val message = "The `$providerValue` provider is deprecated and will not be " +
                        "available when `targetSdkVersion` is P or higher"
                val location = context.getLocation(providerArg)
                val incident = Incident(ISSUE, node, location, message)
                context.report(incident, minSdkLessThan(ANDROID_P_API_VERSION))
            }
        } else {
            // Try to evaluate the expression as a string constant
            val evaluated = providerArg.evaluateString()
            if (evaluated != null && evaluated in DEPRECATED_PROVIDERS) {
                val message = "The `$evaluated` provider is deprecated and will not be " +
                        "available when `targetSdkVersion` is P or higher"
                val location = context.getLocation(providerArg)
                val incident = Incident(ISSUE, node, location, message)
                context.report(incident, minSdkLessThan(ANDROID_P_API_VERSION))
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: com.android.tools.lint.detector.api.LintMap): Boolean {
        if (map.containsKey(KEY_MIN_SDK)) {
            val minSdk = map.getInt(KEY_MIN_SDK) ?: return true
            return minSdk < ANDROID_P_API_VERSION
        }
        return true
    }

    companion object Keys {
        private const val KEY_MIN_SDK = "minSdk"
    }
}