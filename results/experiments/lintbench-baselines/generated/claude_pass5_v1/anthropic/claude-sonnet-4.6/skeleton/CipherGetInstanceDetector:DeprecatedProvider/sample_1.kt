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
                The `BC` provider has been deprecated and will not be provided when \
                `targetSdkVersion` is P or higher.
                """,
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://goo.gle/DeprecatedProvider",
        )

        private const val MIN_SDK_KEY = "minSdk"

        // Classes that have a getInstance(String, String) or getInstance(String, Provider) method
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

        // Deprecated BC providers
        private val DEPRECATED_PROVIDERS = setOf("BC", "BC-FIPS", "BCFIPS")

        private const val GET_INSTANCE = "getInstance"

        // Android P = API 28
        private const val ANDROID_P_API = 28
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

        // getInstance with a provider argument must have at least 2 parameters
        val arguments = node.valueArguments
        if (arguments.size < 2) return

        // The second argument is the provider (either String or Provider)
        val providerArg = arguments[1]

        // We only flag when the provider is a String literal matching a deprecated provider
        if (providerArg !is ULiteralExpression) return
        val providerValue = providerArg.evaluateString() ?: return

        if (providerValue.uppercase() !in DEPRECATED_PROVIDERS.map { it.uppercase() }) return

        val incident = Incident(context)
            .issue(ISSUE)
            .location(context.getLocation(providerArg))
            .message("The `$providerValue` provider is deprecated and will not be available when `targetSdkVersion` is P or higher")
            .scope(node)

        context.report(incident, map().put(MIN_SDK_KEY, context.mainProject.minSdk))
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only report the incident if the target SDK is >= Android P (API 28)
        val minSdk = map.getInt(MIN_SDK_KEY, 1) ?: 1
        if (context.mainProject.targetSdk >= ANDROID_P_API) {
            return true
        }
        // If targetSdkVersion is below P, still warn but it's not a definite problem yet
        // We report if targetSdk >= P or if minSdk >= P
        return minSdk >= ANDROID_P_API
    }
}