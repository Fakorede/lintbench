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
import org.jetbrains.uast.UIfExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val TARGET_CLASS = "androidx.credentials.CreatePublicKeyCredentialRequest"

        private val IMPLEMENTATION = Implementation(
            PublicKeyCredentialDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = "Credential Manager supports creating public key credentials (Passkeys) starting with Android 9 (API level 28). Wrap this call in a version check such as `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)` to avoid runtime failures on older devices.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(TARGET_CLASS)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (isWithinApiGuard(node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            "CreatePublicKeyCredentialRequest requires API level 28 or higher. Add a `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` check before this call.",
        )
    }

    private fun isWithinApiGuard(node: UElement): Boolean {
        var current: UElement? = node
        while (current != null) {
            if (current is UIfExpression) {
                val conditionText = current.condition.sourcePsi?.text
                if (conditionText != null && isApiGuard(conditionText)) {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }

    private fun isApiGuard(conditionText: String): Boolean {
        val text = conditionText.replace("\\s+".toRegex(), " ")
        return API_GUARD_REGEX.containsMatchIn(text)
    }

    private val API_GUARD_REGEX = Regex(
        "SDK_INT\\s*(>=|>|==|!=)\\s*(28|VERSION_CODES\\.P|P\\b)" +
        "|(28|VERSION_CODES\\.P|P\\b)\\s*(<=|<|==|!=)\\s*SDK_INT"
    )
}