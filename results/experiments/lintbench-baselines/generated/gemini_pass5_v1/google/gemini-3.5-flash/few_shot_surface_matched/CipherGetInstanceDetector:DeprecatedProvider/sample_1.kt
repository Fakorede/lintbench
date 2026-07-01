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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val DEPRECATED_PROVIDER = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                See https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html for details.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (!isJCAClass(qualifiedName)) return

        for (argument in node.valueArguments) {
            val value = argument.evaluate()
            if (value == "BC") {
                val incident = Incident(
                    DEPRECATED_PROVIDER,
                    node,
                    context.getLocation(argument),
                    "The `BC` provider has been deprecated and will not be provided"
                )
                context.report(incident)
                break
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk >= 28) {
            incident.message = "The `BC` provider is deprecated and is not provided when `targetSdkVersion` is P or higher"
        } else {
            incident.message = "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher"
        }
        return true
    }

    private fun isJCAClass(className: String): Boolean {
        return className == "javax.crypto.Cipher" ||
               className == "java.security.Signature" ||
               className == "javax.crypto.KeyAgreement" ||
               className == "javax.crypto.Mac" ||
               className == "java.security.KeyPairGenerator" ||
               className == "java.security.KeyFactory" ||
               className == "java.security.AlgorithmParameters" ||
               className == "java.security.SecureRandom" ||
               className == "java.security.MessageDigest" ||
               className == "javax.crypto.KeyGenerator" ||
               className == "javax.crypto.SecretKeyFactory" ||
               className == "java.security.AlgorithmParameterGenerator" ||
               className == "java.security.CertificateFactory" ||
               className == "java.security.KeyStore"
    }
}