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

        private val GET_INSTANCE_METHODS = listOf("getInstance")

        private val CRYPTO_CLASSES = setOf(
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

        private val DEPRECATED_PROVIDERS = setOf("BC", "Crypto")

        private const val KEY_PROVIDER = "provider"
        private const val MIN_SDK_P = 28
    }

    override fun getApplicableMethodNames(): List<String> = GET_INSTANCE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (!CRYPTO_CLASSES.contains(qualifiedName)) {
            return
        }

        // getInstance can be called with:
        //   getInstance(String algorithm)
        //   getInstance(String algorithm, String provider)
        //   getInstance(String algorithm, Provider provider)
        val arguments = node.valueArguments
        if (arguments.size < 2) {
            return
        }

        // The second argument is the provider
        val providerArg = arguments[1]
        val providerValue = (providerArg as? ULiteralExpression)?.evaluateString()
            ?: providerArg.evaluateString()
            ?: return

        if (!DEPRECATED_PROVIDERS.contains(providerValue)) {
            return
        }

        val incident = Incident(context)
            .issue(ISSUE)
            .location(context.getLocation(providerArg))
            .message(
                "The `$providerValue` provider is deprecated and will not be available " +
                    "when `targetSdkVersion` is P or higher"
            )
            .scope(node)

        val map = LintMap()
        map.put(KEY_PROVIDER, providerValue)

        context.report(incident, map)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        // Only report if targetSdkVersion >= P (28)
        val mainProject = context.mainProject
        val targetSdk = mainProject.targetSdk
        return targetSdk >= MIN_SDK_P
    }
}