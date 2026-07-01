package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import java.util.EnumSet

private const val MIN_SDK = 28
private const val CREDENTIAL_MANAGER_CLASS = "androidx.credentials.CredentialManager"
private const val PUBLIC_KEY_REQUEST_CLASS = "androidx.credentials.CreatePublicKeyCredentialRequest"

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (!isCreatePublicKeyCredentialCall(context, node)) return
                if (isGuardedBySdkCheck(context, node, MIN_SDK)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Creating a public key credential (passkey) requires Android 9 (API 28) or higher. " +
                        "Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` before calling this method."
                )
            }
        }
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
            """,
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

private fun isCreatePublicKeyCredentialCall(context: JavaContext, node: UCallExpression): Boolean {
    if (node.methodName != "createCredential") return false

    val receiver = node.receiver ?: return false
    val receiverType = receiver.getExpressionType() ?: return false
    val receiverClass = (receiverType as? PsiClassType)?.resolve() ?: return false
    if (!context.evaluator.extendsClass(receiverClass, CREDENTIAL_MANAGER_CLASS, false)) return false

    return node.valueArguments.any { arg ->
        isPublicKeyCredentialRequest(context, arg)
    }
}

private fun isPublicKeyCredentialRequest(context: JavaContext, expression: UExpression): Boolean {
    val type = expression.getExpressionType()
    val canonical = type?.canonicalText ?: ""
    if (canonical.contains(PUBLIC_KEY_REQUEST_CLASS)) return true

    val cls = (type as? PsiClassType)?.resolve() ?: return false
    return context.evaluator.extendsClass(cls, PUBLIC_KEY_REQUEST_CLASS, false)
}

private fun isGuardedBySdkCheck(context: JavaContext, node: UCallExpression, minSdk: Int): Boolean {
    var current: UElement? = node
    while (current != null) {
        val ifExpr = current.getParentOfType(UIfExpression::class.java, true) ?: break
        if (conditionEnsuresAtLeast(context, ifExpr.condition, minSdk)) return true
        current = ifExpr.uastParent
    }
    return false
}

private fun conditionEnsuresAtLeast(
    context: JavaContext,
    expression: UExpression?,
    minSdk: Int
): Boolean {
    val binary = unwrap(expression) as? UBinaryExpression ?: return false

    return when (binary.operator) {
        UastBinaryOperator.LOGICAL_OR ->
            conditionEnsuresAtLeast(context, binary.leftOperand, minSdk) ||
                    conditionEnsuresAtLeast(context, binary.rightOperand, minSdk)

        UastBinaryOperator.LOGICAL_AND ->
            conditionEnsuresAtLeast(context, binary.leftOperand, minSdk) ||
                    conditionEnsuresAtLeast(context, binary.rightOperand, minSdk)

        UastBinaryOperator.GREATER_OR_EQUALS,
        UastBinaryOperator.GREATER -> {
            val sdkSide = when {
                isSdkIntReference(binary.leftOperand) -> binary.rightOperand
                isSdkIntReference(binary.rightOperand) -> binary.leftOperand
                else -> return false
            }
            val value = ConstantEvaluator.evaluate(context, sdkSide) as? Int ?: return false
            value >= minSdk
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