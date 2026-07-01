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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. \
                Please check for the Android version before calling the method.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf(
            "androidx.credentials.CreatePublicKeyCredentialRequest",
            "androidx.credentials.CreatePublicKeyCredentialRequest.Builder"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val minSdk = context.project.minSdkVersion.featureLevel
        if (minSdk >= 28) {
            return
        }

        if (!isWithinAndroid9Check(node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Creating public key credential requires Android 9 (API 28) or higher, or a surrounding SDK version check"
            )
        }
    }

    private fun isWithinAndroid9Check(node: UElement): Boolean {
        var current: UElement? = node
        while (current != null) {
            val parent = current.uastParent
            if (parent is UIfExpression) {
                val condition = parent.condition
                if (isSdkCheck(condition, 28)) {
                    return true
                }
            }
            current = parent
        }
        return false
    }

    private fun isSdkCheck(expression: UExpression, targetApi: Int): Boolean {
        val text = expression.asSourceString()
        // Simple heuristic to check for SDK_INT checks like SDK_INT >= 28, SDK_INT >= Build.VERSION_CODES.P, etc.
        if (text.contains("SDK_INT")) {
            if (text.contains(">=") || text.contains(">")) {
                if (text.contains("28") || text.contains("P") || text.contains("O_MR1") || text.contains("27")) {
                    return true
                }
            }
        }
        return false
    }
}