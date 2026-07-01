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
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName in CRYPTO_CLASSES) {
            val arguments = node.valueArguments
            if (arguments.size >= 2) {
                val providerArg = arguments[1]
                val evaluated = ConstantEvaluator.evaluate(context, providerArg)
                if (evaluated is String && "BC".equals(evaluated, ignoreCase = true)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(providerArg),
                        "The `BC` provider is deprecated and when targeting Android P or higher, this will throw an exception. " +
                                "Avoid specifying a provider and instead use the default implementation."
                    )
                }
            }
        }
    }

    companion object {
        private val CRYPTO_CLASSES = setOf(
            "javax.crypto.Cipher",
            "java.security.Signature",
            "java.security.KeyPairGenerator",
            "javax.crypto.KeyGenerator",
            "javax.crypto.Mac",
            "java.security.KeyStore",
            "javax.crypto.SecretKeyFactory",
            "java.security.cert.CertificateFactory",
            "javax.crypto.KeyAgreement",
            "java.security.AlgorithmParameters",
            "java.security.AlgorithmParameterGenerator",
            "java.security.SecureRandom"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                
                Reference documentation:
                - https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html
                - https://goo.gle/DeprecatedProvider
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}