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
        private const val BC_PROVIDER = "BC"
        private const val MIN_SDK_KEY = "minSdk"
        // Android P == API 28
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

        if (qualifiedName !in CLASSES_WITH_GET_INSTANCE) {
            return
        }

        // getInstance can have 1 or 3 arguments:
        //   getInstance(String algorithm)
        //   getInstance(String algorithm, String provider)
        //   getInstance(String algorithm, Provider provider)
        // We care about the provider argument (2nd arg when present)
        val arguments = node.valueArguments
        if (arguments.size < 2) return

        val providerArg = arguments[1]
        val providerValue = if (providerArg is ULiteralExpression) {
            providerArg.evaluateString()
        } else {
            providerArg.evaluateString()
        } ?: return

        if (providerValue != BC_PROVIDER) return

        val incident = Incident(context)
            .issue(ISSUE)
            .location(context.getLocation(providerArg))
            .message("The BC provider is deprecated and when `targetSdkVersion` is P or higher, " +
                    "it will not be available")
            .scope(node)

        context.report(incident, map().put(MIN_SDK_KEY, context.mainProject.minSdk))
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only report the incident if the targetSdkVersion is >= Android P (API 28)
        val targetSdk = context.mainProject.targetSdk
        if (targetSdk >= ANDROID_P_API) {
            return true
        }
        // Also check minSdk stored in map
        val minSdk = map.getInt(MIN_SDK_KEY) ?: 1
        if (minSdk >= ANDROID_P_API) {
            return true
        }
        return false
    }
}