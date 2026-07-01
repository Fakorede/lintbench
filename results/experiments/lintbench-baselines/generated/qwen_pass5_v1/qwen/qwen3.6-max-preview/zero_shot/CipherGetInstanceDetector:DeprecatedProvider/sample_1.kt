package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class CipherGetInstanceDetector : Detector(), UastScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        if (!isRelevantCryptoClass(qualifiedName)) return

        val evaluator = context.evaluator
        for (arg in node.valueArguments) {
            val value = evaluator.getStringValue(arg) ?: continue
            if (value == "BC" || value.endsWith("/BC")) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(arg),
                    "Using the BC provider is deprecated and will fail on Android P and higher"
                )
                return
            }
        }
    }

    private fun isRelevantCryptoClass(qualifiedName: String): Boolean {
        return qualifiedName == "javax.crypto.Cipher" ||
               qualifiedName == "java.security.MessageDigest" ||
               qualifiedName == "java.security.Signature" ||
               qualifiedName == "java.security.KeyFactory" ||
               qualifiedName == "java.security.KeyPairGenerator" ||
               qualifiedName == "java.security.SecureRandom" ||
               qualifiedName == "javax.crypto.Mac" ||
               qualifiedName == "javax.crypto.KeyGenerator" ||
               qualifiedName == "javax.crypto.SecretKeyFactory"
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "DeprecatedProvider",
            "Using BC Provider",
            "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher. " +
            "See https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html",
            Category.SECURITY,
            6,
            Severity.WARNING,
            Implementation(
                CipherGetInstanceDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}