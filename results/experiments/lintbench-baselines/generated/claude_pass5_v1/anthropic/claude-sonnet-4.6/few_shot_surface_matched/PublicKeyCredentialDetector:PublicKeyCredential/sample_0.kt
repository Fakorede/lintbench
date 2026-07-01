package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PUBLIC_KEY_CREDENTIAL_CREATE_REQUEST =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val PUBLIC_KEY_CREDENTIAL =
            "androidx.credentials.PublicKeyCredential"
        private const val ANDROID_P_API_LEVEL = 28

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) \
                starting Android 9 or higher. Please check for the Android version before \
                calling the method.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        PUBLIC_KEY_CREDENTIAL_CREATE_REQUEST,
        PUBLIC_KEY_CREDENTIAL
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!isGuardedByVersionCheck(node)) {
            val className = constructor.containingClass?.qualifiedName ?: return
            val simpleName = constructor.containingClass?.name ?: className
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Creating `$simpleName` requires Android 9 (API level $ANDROID_P_API_LEVEL) " +
                        "or higher. Please check the Android version before calling this constructor."
            )
        }
    }

    private fun isGuardedByVersionCheck(node: UCallExpression): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                val condition = parent.condition
                val conditionText = condition.asSourceString()
                if (containsVersionCheck(conditionText)) {
                    return true
                }
            }
            if (parent is UMethod) {
                break
            }
            parent = parent.uastParent
        }

        val enclosingIf = node.getParentOfType<UIfExpression>(strict = true)
        if (enclosingIf != null) {
            val conditionText = enclosingIf.condition.asSourceString()
            if (containsVersionCheck(conditionText)) {
                return true
            }
        }

        return false
    }

    private fun containsVersionCheck(conditionText: String): Boolean {
        return conditionText.contains("SDK_INT") ||
                conditionText.contains("VERSION.SDK") ||
                conditionText.contains("isAtLeast") ||
                conditionText.contains("BuildCompat")
    }
}