package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("androidx.credentials.CreatePublicKeyCredentialRequest")

    override fun visitConstructor(
        context: JavaContext,
        call: UCallExpression,
        constructor: PsiElement
    ) {
        if (context.project.minSdkVersion >= 28) {
            return
        }

        if (isWithinSdkIntCheck(call, 28)) {
            return
        }

        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            "Creating public key credentials (Passkeys) requires Android 9 (API 28) or higher. " +
                "Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` before calling this API."
        )
    }

    private fun isWithinSdkIntCheck(element: UElement, requiredApi: Int): Boolean {
        var current: UElement? = element
        while (current != null) {
            if (current is UIfExpression) {
                val condition = current.condition
                if (condition != null && isSdkIntAtLeastCheck(condition, requiredApi)) {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }

    private fun isSdkIntAtLeastCheck(condition: UExpression, requiredApi: Int): Boolean {
        val conditionText = condition.sourcePsi?.text ?: return false
        if (!conditionText.contains("SDK_INT")) return false
        if (!conditionText.contains(">=") && !conditionText.contains(">")) return false
        if (conditionText.contains("VERSION_CODES.P")) return true
        return conditionText.contains(Regex("""\b2[89]\b""")) ||
            conditionText.contains(Regex("""\b[3-9]\d\b"""))
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            PublicKeyCredentialDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credentials requires Android 9+",
            explanation = """
                Credential Manager supports creating public key credentials (Passkeys) only on Android 9 (API 28) and higher.
                You must check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` before calling this API.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}