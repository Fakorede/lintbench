package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.*
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
        val minSdk = context.mainProject.minSdkVersion?.apiLevel ?: 1
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
        if (method != null && hasApiAnnotation(context, method)) return true
        val cls = node.getParentOfType(UClass::class.java, true)
        if (cls != null && hasApiAnnotation(context, cls)) return true

        var parent = node.uastParent
        while (parent != null) {
            if (parent is UIfExpression) {
                if (isSdkIntCheck(context, parent.condition)) return true
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun hasApiAnnotation(context: JavaContext, element: UElement): Boolean {
        val psi = element.sourcePsi as? PsiModifierListOwner ?: return false
        val annotations = context.evaluator.getAllAnnotations(psi, false)
        for (ann in annotations) {
            val qName = ann.qualifiedName
            if (qName == "android.annotation.RequiresApi" || qName == "androidx.annotation.RequiresApi" ||
                qName == "android.annotation.TargetApi" || qName == "androidx.annotation.TargetApi") {
                return true
            }
        }
        return false
    }

    private fun isSdkIntCheck(context: JavaContext, condition: UExpression?): Boolean {
        if (condition == null) return false
        if (condition is UBinaryExpression) {
            val opStr = condition.operator.toString()
            if (opStr == "&&") {
                return isSdkIntCheck(context, condition.leftOperand) || isSdkIntCheck(context, condition.rightOperand)
            }
            val leftSrc = condition.leftOperand.asSourceString()
            val rightSrc = condition.rightOperand.asSourceString()
            val leftIsSdk = leftSrc.contains("SDK_INT")
            val rightIsSdk = rightSrc.contains("SDK_INT")
            if (!leftIsSdk && !rightIsSdk) return false

            val constExpr = if (leftIsSdk) condition.rightOperand else condition.leftOperand
            val constVal = (constExpr.evaluate() as? Number)?.toInt() ?: return false

            return when (opStr) {
                ">=" -> constVal >= MIN_API
                ">" -> constVal >= MIN_API - 1
                else -> false
            }
        }
        return false
    }
}