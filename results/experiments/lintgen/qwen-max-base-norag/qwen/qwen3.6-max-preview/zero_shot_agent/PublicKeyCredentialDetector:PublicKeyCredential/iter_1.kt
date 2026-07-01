package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
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

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val containingMethod = node.getContainingUMethod()
        if (containingMethod != null) {
            val psiMethod = containingMethod.javaPsi
            val evaluator = context.evaluator
            if (evaluator.findAnnotation(psiMethod, "androidx.annotation.RequiresApi") != null ||
                evaluator.findAnnotation(psiMethod, "android.annotation.TargetApi") != null) {
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
}