package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        method: PsiMethod?
    ) {
        method ?: return
        val className = method.containingClass?.qualifiedName ?: return
        if (className !in CRYPTO_CLASSES) return

        val args = call.valueArguments
        if (args.size < 2) return

        val provider = ConstantEvaluator.evaluateString(context, args[1], false)
        if (provider == "BC") {
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "The BC provider has been deprecated and will not be provided when targetSdkVersion is P or higher."
            )
        }
    }

    companion object {
        private val CRYPTO_CLASSES = setOf(
            "javax.crypto.Cipher",
            "javax.crypto.KeyAgreement",
            "javax.crypto.KeyGenerator",
            "javax.crypto.Mac",
            "javax.crypto.SecretKeyFactory",
            "java.security.KeyPairGenerator",
            "java.security.KeyStore",
            "java.security.MessageDigest",
            "java.security.SecureRandom",
            "java.security.Signature"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion`
                is P or higher. Use an alternative provider or explicitly include Bouncy Castle in
                your application if required.
            """.trimIndent(),
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/DeprecatedProvider"
        )
    }
}