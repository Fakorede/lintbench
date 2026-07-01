package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CREATE_PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val GET_PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.GetPublicKeyCredentialOption"

        private val TARGET_CONSTRUCTORS = setOf(
            CREATE_PUBLIC_KEY_CREDENTIAL_CLASS,
            GET_PUBLIC_KEY_CREDENTIAL_CLASS
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) \
                starting Android 9 (API level 28) or higher. Please check for the Android \
                version before calling the method.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val MIN_SDK_VERSION = 28
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return TARGET_CONSTRUCTORS.toList()
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (isWithinSdkVersionCheck(node)) {
            return
        }

        val minSdk = context.mainProject.minSdk
        if (minSdk >= MIN_SDK_VERSION) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Credential Manager API supports creating public key credential (Passkeys) " +
                "starting Android 9 (API level 28) or higher. Please check for the Android " +
                "version before calling the method."
        )
    }

    private fun isWithinSdkVersionCheck(node: UElement): Boolean {
        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                val condition = parent.condition.asSourceString()
                if (containsSdkVersionCheck(condition)) {
                    return true
                }
            }
            if (parent is UMethod) {
                break
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun containsSdkVersionCheck(condition: String): Boolean {
        val sdkIntPatterns = listOf(
            "SDK_INT",
            "getSdkVersion",
            "getApiLevel"
        )
        return sdkIntPatterns.any { condition.contains(it) }
    }
}