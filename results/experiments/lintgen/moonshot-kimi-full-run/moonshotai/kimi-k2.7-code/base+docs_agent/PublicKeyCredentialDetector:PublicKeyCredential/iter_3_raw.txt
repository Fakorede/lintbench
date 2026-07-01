package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.getParentOfType
import java.util.EnumSet

private const val MIN_SDK = 28
private const val CREDENTIAL_MANAGER_CLASS = "androidx.credentials.CredentialManager"
private const val CREATE_CREDENTIAL_REQUEST_CLASS = "androidx.credentials.CreateCredentialRequest"
private const val PUBLIC_KEY_REQUEST_CLASS = "androidx.credentials.CreatePublicKeyCredentialRequest"

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("createCredential", "createCredentialAsync")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!isCredentialManagerCreateCredential(context, node, method)) return
        if (node.valueArguments.none { isPublicKeyCredentialRequest(context, it) }) return
        if (isGuardedBySdkCheck(node, MIN_SDK)) return

        context.report(
            ISSUE,
            node,
            context.getNameLocation(node),
            "Creating a public key credential (passkey) requires Android 9 (API 28) or higher. " +
                "Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` before calling this method."
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Public key credential creation requires Android 9+",
            explanation = """
                Credential Manager supports creating public key credentials (passkeys) starting with Android 9 (API level 28).
                Calling `CredentialManager.createCredential(...)` with a `CreatePublicKeyCredentialRequest` on older devices can fail at runtime.
                Always guard the call with a `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` check.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}

private fun isCredentialManagerCreateCredential(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod
): Boolean {
    val containingClass = method.containingClass
    if (containingClass != null && context.evaluator.extendsClass(containingClass, CREDENTIAL_MANAGER_CLASS, false)) {
        return true
    }

    val receiver = node.receiver
    val receiverType = receiver?.getExpressionType()
    val receiverClass = (receiverType as? PsiClassType)?.resolve()
    if (receiverClass != null && context.evaluator.extendsClass(receiverClass, CREDENTIAL_MANAGER_CLASS, false)) {
        return true
    }

    return node.valueArguments.any { isCreateCredentialRequest(context, it) }
}

private fun isPublicKeyCredentialRequest(context: JavaContext, expression: UExpression?): Boolean {
    if (expression == null) return false

    val expressionType = expression.getExpressionType()
    if (expressionType != null && typeExtends(context, expressionType, PUBLIC_KEY_REQUEST_CLASS)) {
        return true
    }

    if (expression is UCallExpression && expression.kind == UastCallKind.CONSTRUCTOR_CALL) {
        val classReference = expression.classReference
        val resolved = classReference?.resolve()
        if (resolved is PsiClass && context.evaluator.extendsClass(resolved, PUBLIC_KEY_REQUEST_CLASS, false)) {
            return true
        }
    }

    return false
}

private fun isCreateCredentialRequest(context: JavaContext, expression: UExpression?): Boolean {
    if (expression == null) return false
    val expressionType = expression.getExpressionType() ?: return false
    return typeExtends(context, expressionType, CREATE_CREDENTIAL_REQUEST_CLASS)
}

private fun typeExtends(context: JavaContext, type: PsiType, className: String): Boolean {
    val cls = (type as? PsiClassType)?.resolve() ?: return false
    return context.evaluator.extendsClass(cls, className, false)
}

private fun isGuardedBySdkCheck(node: UCallExpression, minSdk: Int): Boolean {
    var current: UElement? = node
    while (current != null) {
        val ifExpr = current.getParentOfType(UIfExpression::class.java, true) ?: break
        if (conditionEnsuresAtLeast(ifExpr.condition, minSdk)) return true
        current = ifExpr.uastParent
    }
    return false
}

private fun conditionEnsuresAtLeast(expression: UExpression?, minSdk: Int): Boolean {
    val binary = unwrap(expression) as? UBinaryExpression ?: return false

    return when (binary.operator) {
        UastBinaryOperator.GREATER_OR_EQUALS,
        UastBinaryOperator.EQUALS,
        UastBinaryOperator.IDENTITY_EQUALS -> {
            val sdkSide = when {
                isSdkIntReference(binary.leftOperand) -> binary.rightOperand
                isSdkIntReference(binary.rightOperand) -> binary.leftOperand
                else -> return false
            }
            val value = ConstantEvaluator.evaluate(null, sdkSide) as? Int ?: return false
            value >= minSdk
        }
        UastBinaryOperator.GREATER -> {
            val sdkSide = when {
                isSdkIntReference(binary.leftOperand) -> binary.rightOperand
                isSdkIntReference(binary.rightOperand) -> binary.leftOperand
                else -> return false
            }
            val value = ConstantEvaluator.evaluate(null, sdkSide) as? Int ?: return false
            value >= minSdk - 1
        }
        UastBinaryOperator.LOGICAL_AND -> {
            conditionEnsuresAtLeast(binary.leftOperand, minSdk) ||
                    conditionEnsuresAtLeast(binary.rightOperand, minSdk)
        }
        UastBinaryOperator.LOGICAL_OR -> {
            conditionEnsuresAtLeast(binary.leftOperand, minSdk) &&
                    conditionEnsuresAtLeast(binary.rightOperand, minSdk)
        }
        else -> false
    }
}

private fun isSdkIntReference(expression: UExpression?): Boolean {
    val ref = unwrap(expression) as? UReferenceExpression ?: return false
    val field = ref.resolve() as? PsiField ?: return false
    return field.containingClass?.qualifiedName == "android.os.Build.VERSION" && field.name == "SDK_INT"
}

private fun unwrap(expression: UExpression?): UExpression? {
    var current = expression
    while (current is UParenthesizedExpression) {
        current = current.expression
    }
    return current
}