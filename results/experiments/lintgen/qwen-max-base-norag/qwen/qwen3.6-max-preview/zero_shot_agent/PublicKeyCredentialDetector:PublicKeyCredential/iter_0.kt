package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUMethod
import java.util.EnumSet

class PublicKeyCredentialDetector : Detector(), Detector.UastScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PublicKeyCredentialDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential requires Android 9+",
            explanation = "Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. Please check for the Android version before calling the method.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf("androidx.credentials.CreatePublicKeyCredentialRequest")
    }

    override fun visitConstructorCall(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val method = node.getContainingUMethod()
        if (method != null) {
            val psiMethod = method.javaPsi
            val evaluator = context.evaluator

            val requiresApi = evaluator.findAnnotation(psiMethod, "androidx.annotation.RequiresApi")
            val targetApi = evaluator.findAnnotation(psiMethod, "android.annotation.TargetApi")

            if (isApiGuarded(requiresApi) || isApiGuarded(targetApi)) {
                return
            }
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating public key credential requires Android 9 (API 28) or higher. " +
            "Please check for the Android version before calling this method."
        )
    }

    private fun isApiGuarded(annotation: PsiAnnotation?): Boolean {
        if (annotation == null) return false
        val valueText = annotation.findAttributeValue("value")?.text ?: return false
        val apiLevel = valueText.toIntOrNull() ?: when {
            valueText.contains("P") || valueText.contains("28") -> 28
            valueText.contains("Q") || valueText.contains("29") -> 29
            valueText.contains("R") || valueText.contains("30") -> 30
            valueText.contains("S") || valueText.contains("31") -> 31
            valueText.contains("T") || valueText.contains("33") -> 33
            valueText.contains("U") || valueText.contains("34") -> 34
            else -> 0
        }
        return apiLevel >= 28
    }
}