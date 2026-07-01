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
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastBinaryOperator

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            "PublicKeyCredential",
            "Creating public key credential",
            "Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. Please check for the Android version before calling the method.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(PublicKeyCredentialDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
        private const val MIN_SDK = 28
    }

    override fun getApplicableMethodNames(): List<String> = listOf("createCredential")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val cls = method.containingClass?.qualifiedName ?: return
        if (cls == "android.credentials.CredentialManager" || cls == "androidx.credentials.CredentialManager") {
            if (!isVersionGuarded(context, node) && !hasRequiresApiAnnotation(context, node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Creating public key credential requires Android 9 (API 28) or higher. Please check for the Android version before calling this method."
                )
            }
        }
    }

    private fun isVersionGuarded(context: JavaContext, node: UElement): Boolean {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UIfExpression) {
                if (checksSdkIntAtLeast(current.condition, MIN_SDK)) {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }

    private fun checksSdkIntAtLeast(condition: UExpression?, minSdk: Int): Boolean {
        if (condition == null) return false
        if (condition is UBinaryExpression) {
            val left = condition.leftOperand
            val right = condition.rightOperand
            val op = condition.operator
            if (isSdkInt(left) && right is ULiteralExpression && right.value is Int) {
                val value = right.value as Int
                return when (op) {
                    UastBinaryOperator.GREATER_OR_EQUALS -> value >= minSdk
                    UastBinaryOperator.GREATER -> value >= minSdk - 1
                    else -> false
                }
            }
            if (isSdkInt(right) && left is ULiteralExpression && left.value is Int) {
                val value = left.value as Int
                return when (op) {
                    UastBinaryOperator.LESS_OR_EQUALS -> value >= minSdk
                    UastBinaryOperator.LESS -> value >= minSdk - 1
                    else -> false
                }
            }
            if (op == UastBinaryOperator.LOGICAL_AND) {
                return checksSdkIntAtLeast(left, minSdk) || checksSdkIntAtLeast(right, minSdk)
            }
        }
        return false
    }

    private fun isSdkInt(expr: UExpression): Boolean {
        val render = expr.asRenderString()
        return render == "Build.VERSION.SDK_INT" || render == "android.os.Build.VERSION.SDK_INT"
    }

    private fun hasRequiresApiAnnotation(context: JavaContext, node: UElement): Boolean {
        var current: UElement? = node
        while (current != null) {
            val psi = current.sourcePsi
            if (psi is PsiModifierListOwner) {
                val annotations = psi.modifierList?.annotations ?: emptyArray()
                for (ann in annotations) {
                    val name = ann.qualifiedName
                    if (name?.endsWith("RequiresApi") == true || name?.endsWith("TargetApi") == true) {
                        return true
                    }
                }
            }
            current = current.uastParent
        }
        return false
    }
}