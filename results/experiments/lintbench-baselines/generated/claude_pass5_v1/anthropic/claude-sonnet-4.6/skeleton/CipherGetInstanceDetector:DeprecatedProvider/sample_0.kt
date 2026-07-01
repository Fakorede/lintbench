package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided \
                when `targetSdkVersion` is P or higher.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://goo.gle/DeprecatedProvider",
        )

        private const val GET_INSTANCE = "getInstance"
        private const val MIN_SDK_KEY = "minSdk"

        // Classes that have a getInstance(String, String/Provider) method we care about
        private val RELEVANT_CLASSES = setOf(
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

        // Deprecated BC provider names
        private val DEPRECATED_PROVIDERS = setOf("BC", "BC-FIPS", "BCFIPS")

        // Android P API level
        private const val ANDROID_P_API_LEVEL = 28
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Check that the method belongs to one of the relevant classes
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (qualifiedName !in RELEVANT_CLASSES) return

        // getInstance(String algorithm, String provider) or
        // getInstance(String algorithm, Provider provider)
        val arguments = node.valueArguments
        if (arguments.size < 2) return

        // The provider is the second argument
        val providerArg = arguments[1]

        // We only flag string literal provider names that match the deprecated BC provider
        if (providerArg !is ULiteralExpression) return
        val providerName = providerArg.evaluateString() ?: return
        if (providerName !in DEPRECATED_PROVIDERS) return

        val message = "The `$providerName` provider is deprecated and will not be available " +
            "when `targetSdkVersion` is P or higher"

        val incident = Incident(ISSUE, node, context.getLocation(providerArg), message)
        context.report(incident, map().put(MIN_SDK_KEY, context.mainProject.minSdk))
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only report the incident if the project targets Android P (API 28) or higher,
        // or if the minSdk is already >= P (in which case it's definitely broken).
        // We warn whenever targetSdk >= P or minSdk >= P.
        val targetSdk = context.mainProject.targetSdk
        val minSdk = map.getInt(MIN_SDK_KEY, 1) ?: 1

        return if (targetSdk >= ANDROID_P_API_LEVEL || minSdk >= ANDROID_P_API_LEVEL) {
            // Update severity or message based on whether it's definitely broken
            true
        } else {
            // Target SDK is below P, the BC provider is still available
            false
        }
    }
}