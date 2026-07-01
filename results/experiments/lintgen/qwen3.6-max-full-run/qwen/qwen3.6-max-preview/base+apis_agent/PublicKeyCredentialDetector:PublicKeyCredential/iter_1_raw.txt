package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {
    companion object {
        private const val MIN_API = 28
        private const val TARGET_CLASS = "androidx.credentials.CreatePublicKeyCredentialRequest"

        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = "Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. Please check for the Android version before calling the method.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(TARGET_CLASS)

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        val minSdk = context.mainProject.minSdkVersion.apiLevel
        if (minSdk >= MIN_API) return
        if (isVersionGuarded(context, node)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating public key credential requires Android 9 (API $MIN_API) or higher. " +
            "Please check for the Android version before calling this constructor."
        )
    }

    private fun isVersionGuarded(context: JavaContext, node: UCallExpression): Boolean {
        val method = node.getParentOfType(UMethod::class.java, true)
        if (method != null && hasApiAnnotation(context, method, MIN_API)) return true
        val cls = node.getParentOfType(UClass::class.java, true)
        if (cls != null && hasApiAnnotation(context, cls, MIN_API)) return true

        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                if (isSdkIntCheck(context, parent.condition, MIN_API)) return true
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun hasApiAnnotation(context: JavaContext, element: UElement, minApi: Int): Boolean {
        val annotations = listOf(
            "android.annotation.RequiresApi", "android.annotation.TargetApi",
            "androidx.annotation.RequiresApi", "androidx.annotation.TargetApi"
        )
        val annotated = element as? UAnnotated ?: return false
        for (ann in annotated.annotations) {
            if (ann.qualifiedName in annotations) {
                val value = ann.findAttributeValue("value")
                if (value != null) {
                    val apiLevel = context.evaluator.getIntValue(value as? UExpression)
                    if (apiLevel != null && apiLevel >= minApi) return true
                }
            }
        }
        return false
    }

    private fun isSdkIntCheck(context: JavaContext, condition: UExpression?, minApi: Int): Boolean {
        if (condition == null) return false
        if (condition is UBinaryExpression) {
            if (condition.operator == UastBinaryOperator.LOGICAL_AND) {
                return isSdkIntCheck(context, condition.leftOperand, minApi) ||
                       isSdkIntCheck(context, condition.rightOperand, minApi)
            }
            val left = condition.leftOperand
            val right = condition.rightOperand
            val op = condition.operator
            val leftIsSdk = left.asSourceString().contains("SDK_INT")
            val rightIsSdk = right.asSourceString().contains("SDK_INT")
            if (!leftIsSdk && !rightIsSdk) return false

            val constExpr = if (leftIsSdk) right else left
            val constVal = context.evaluator.getIntValue(constExpr as? UExpression) ?: return false

            return when (op) {
                UastBinaryOperator.GREATER_OR_EQUAL -> constVal >= minApi
                UastBinaryOperator.GREATER -> constVal >= minApi - 1
                else -> false
            }
        }
        return false
    }
}