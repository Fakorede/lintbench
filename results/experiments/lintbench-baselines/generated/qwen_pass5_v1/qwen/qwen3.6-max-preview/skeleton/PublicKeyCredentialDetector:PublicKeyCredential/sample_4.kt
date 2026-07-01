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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PublicKeyCredentialDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = "Credential Manager API supports creating public key credential (Passkeys) starting Android 9 (API 28) or higher. Please check for the Android version before calling the method.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? = listOf(
        "androidx.credentials.CreatePublicKeyCredentialRequest",
        "android.credentials.CreatePublicKeyCredentialRequest"
    )

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        val minSdk = context.mainProject.minSdkVersion?.apiLevel ?: 1
        if (minSdk >= 28) return

        val containingMethod = node.getParentOfType(UMethod::class.java, true)
        if (containingMethod != null && isGuardedByApiAnnotation(context, containingMethod)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating public key credential requires Android 9 (API 28) or higher. " +
                "Please check for the Android version before calling this constructor."
        )
    }

    private fun isGuardedByApiAnnotation(context: JavaContext, method: UMethod): Boolean {
        val evaluator = context.evaluator
        val annotations = listOf(
            "androidx.annotation.RequiresApi",
            "android.annotation.RequiresApi",
            "android.annotation.TargetApi"
        )
        for (annName in annotations) {
            val ann = evaluator.getAnnotation(method, annName)
            if (ann != null) {
                val value = ann.findAttributeValue("value")?.text?.toIntOrNull() ?: 0
                if (value >= 28) return true
            }
        }
        return false
    }
}