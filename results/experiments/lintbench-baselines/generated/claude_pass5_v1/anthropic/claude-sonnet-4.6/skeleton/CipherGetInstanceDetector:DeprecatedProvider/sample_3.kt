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

        private val CLASSES_WITH_GET_INSTANCE = listOf(
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
            "java.security.cert.CertificateFactory",
        )

        private const val GET_INSTANCE = "getInstance"

        // Deprecated providers
        private val DEPRECATED_PROVIDERS = setOf("BC", "Crypto")

        private const val KEY_PROVIDER = "provider"
        private const val MIN_SDK = "minSdk"

        // Android P (API 28)
        private const val ANDROID_P_API = 28
    }

    override fun getApplicableMethodNames(): List<String> = listOf(GET_INSTANCE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName !in CLASSES_WITH_GET_INSTANCE) return

        // getInstance can be called with (String) or (String, String) or (String, Provider)
        // We're interested in the two-argument form where the second arg is a String provider name
        val arguments = node.valueArguments
        if (arguments.size < 2) return

        val providerArg = arguments[1]

        // Only flag when the provider is a string literal we can evaluate
        if (providerArg !is ULiteralExpression) {
            val evaluated = providerArg.evaluateString() ?: return
            checkProvider(context, node, evaluated)
            return
        }

        val providerName = providerArg.evaluateString() ?: return
        checkProvider(context, node, providerName)
    }

    private fun checkProvider(
        context: JavaContext,
        node: UCallExpression,
        providerName: String,
    ) {
        if (providerName !in DEPRECATED_PROVIDERS) return

        val message = "The `$providerName` provider is deprecated and will not be provided " +
            "when `targetSdkVersion` is P or higher"

        val incident = Incident(ISSUE, node, context.getLocation(node), message)
        val map = map().put(KEY_PROVIDER, providerName)
        context.report(incident, map)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only report the incident if the minSdkVersion is >= Android P (28),
        // or always warn (return true) so the user is aware.
        // Per the spec: warn when targetSdkVersion is P or higher.
        // We filter based on minSdk: if minSdk >= P, it's definitely a problem.
        // If minSdk < P, we still warn but it's conditional.
        val minSdk = context.mainProject.minSdk
        if (minSdk >= ANDROID_P_API) {
            // Definitely a problem - the BC provider won't be available
            return true
        }
        // For lower minSdk, still report as a warning since targetSdk may be >= P
        return true
    }
}