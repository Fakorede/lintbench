package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod

class PublicKeyCredentialDetector : Detector(), UastScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential requires Android 9+",
            explanation = "Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. Please check for the Android version before calling the method.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val MIN_SDK = 28
        private val APPLICABLE_METHODS = listOf("createCredential", "createPublicKeyCredential")
    }

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val qualifiedName = evaluator.getMemberQualifiedName(method) ?: return

        if (!qualifiedName.contains("Credential", ignoreCase = true) &&
            !qualifiedName.contains("credentials", ignoreCase = true)) {
            return
        }

        val minSdk = context.mainProject.minSdk
        if (minSdk >= MIN_SDK) return

        val containingMethod = getContainingUMethod(node)
        if (containingMethod?.sourcePsi is PsiModifierListOwner) {
            if (hasMinApiAnnotation(evaluator, containingMethod.sourcePsi as PsiModifierListOwner, MIN_SDK)) return
        }

        val containingClass = getContainingUClass(node)
        if (containingClass?.sourcePsi is PsiModifierListOwner) {
            if (hasMinApiAnnotation(evaluator, containingClass.sourcePsi as PsiModifierListOwner, MIN_SDK)) return
        }

        context.report(
            ISSUE,
            node,
            context.getNameLocation(node),
            "Creating public key credential requires API level $MIN_SDK (Android 9) or higher. " +
            "Please check for the Android version before calling this method."
        )
    }

    private fun hasMinApiAnnotation(evaluator: JavaEvaluator, element: PsiModifierListOwner, minApi: Int): Boolean {
        for (annotationName in listOf("androidx.annotation.RequiresApi", "android.annotation.TargetApi")) {
            val annotation = evaluator.findAnnotation(element, annotationName)
            if (annotation != null) {
                val value = annotation.findAttributeValue("value")
                val apiLevel = evaluator.getIntValue(value)
                if (apiLevel != null && apiLevel >= minApi) return true
            }
        }
        return false
    }
}