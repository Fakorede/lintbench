package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credentials requires a version check",
            explanation = """
                Credential Manager public key credentials (Passkeys) are only supported on
                Android 9 (API 28) and higher. Callers must verify
                `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` before invoking
                `CredentialManager.createCredential` or `createCredentialAsync` with a
                `CreatePublicKeyCredentialRequest`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val CREDENTIAL_MANAGER = "androidx.credentials.CredentialManager"
        private const val CREATE_PUBLIC_KEY_REQUEST = "CreatePublicKeyCredentialRequest"
        private const val MIN_SDK = 28 // Build.VERSION_CODES.P
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("createCredential", "createCredentialAsync")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod?) {
        if (context.project.minSdk.apiLevel >= MIN_SDK) {
            return
        }

        val evaluator = context.evaluator
        val isCredentialManager = method != null && (
            evaluator.isMemberInClass(method, CREDENTIAL_MANAGER) ||
                method.containingClass?.qualifiedName == CREDENTIAL_MANAGER
        ) || isCredentialManagerReceiver(context, node)

        if (!isCredentialManager) {
            return
        }

        var hasPublicKeyRequest = false
        for (arg in node.valueArguments) {
            val argType = arg.getExpressionType()
            val typeName = argType?.canonicalText ?: arg.sourcePsi?.text ?: continue
            if (typeName.contains(CREATE_PUBLIC_KEY_REQUEST)) {
                hasPublicKeyRequest = true
                break
            }
        }

        if (!hasPublicKeyRequest) {
            return
        }

        if (isWithinVersionCheck(node) || isPrecededByReturnGuard(node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating public key credentials (Passkeys) requires Android 9 (API 28) or higher; add a version check first."
        )
    }

    private fun isCredentialManagerReceiver(context: JavaContext, call: UCallExpression): Boolean {
        val receiver = call.receiver ?: return false
        val receiverType = receiver.getExpressionType() ?: return false
        return context.evaluator.extendsClass(receiverType.resolve(), CREDENTIAL_MANAGER, false)
    }

    private fun isWithinVersionCheck(call: UCallExpression): Boolean {
        var element: UElement? = call
        while (element != null) {
            val parent = element.uastParent
            if (parent is UIfExpression) {
                val thenExpr = parent.thenExpression
                val elseExpr = parent.elseExpression
                if (element == thenExpr && isVersionCheck(parent.condition)) {
                    return true
                }
                if (element == elseExpr && isOlderVersionCheck(parent.condition)) {
                    return true
                }
            }
            element = parent
        }
        return false
    }

    private fun isPrecededByReturnGuard(call: UCallExpression): Boolean {
        val block = call.getParentOfType(UBlockExpression::class.java, false)
            ?: return false
        val expressions = block.expressions
        val index = expressions.indexOf(call)
        if (index < 0) return false

        for (i in 0 until index) {
            val statement = expressions[i]
            if (statement is UIfExpression &&
                isOlderVersionCheck(statement.condition) &&
                containsReturn(statement.thenExpression)
            ) {
                return true
            }
        }
        return false
    }

    private fun isVersionCheck(condition: UExpression?): Boolean {
        if (condition is UParenthesizedExpression) {
            return isVersionCheck(condition.expression)
        }
        if (condition is UBinaryExpression &&
            condition.operator == UastBinaryOperator.LOGICAL_AND
        ) {
            return isVersionCheck(condition.leftOperand) ||
                isVersionCheck(condition.rightOperand)
        }
        return isVersionComparison(condition, older = false)
    }

    private fun isOlderVersionCheck(condition: UExpression?): Boolean {
        if (condition is UParenthesizedExpression) {
            return isOlderVersionCheck(condition.expression)
        }
        return isVersionComparison(condition, older = true)
    }

    private fun isVersionComparison(condition: UExpression?, older: Boolean): Boolean {
        if (condition !is UBinaryExpression) return false
        val op = condition.operator
        val matchesOperator = if (older) {
            op == UastBinaryOperator.LESS || op == UastBinaryOperator.LESS_OR_EQUALS
        } else {
            op == UastBinaryOperator.GREATER || op == UastBinaryOperator.GREATER_OR_EQUALS
        }
        return matchesOperator && referencesSdkIntAndP(condition.leftOperand, condition.rightOperand)
    }

    private fun referencesSdkIntAndP(left: UExpression?, right: UExpression?): Boolean {
        if (left == null || right == null) return false
        return isSdkIntReference(left) && (isVersionPReference(right) || isApi28(right))
    }

    private fun isSdkIntReference(expr: UExpression): Boolean {
        return expr.sourcePsi?.text?.contains("SDK_INT") == true
    }

    private fun isVersionPReference(expr: UExpression): Boolean {
        return expr.sourcePsi?.text?.contains("VERSION_CODES.P") == true
    }

    private fun isApi28(expr: UExpression): Boolean {
        return expr.sourcePsi?.text?.trim() == "28"
    }

    private fun containsReturn(expr: UExpression?): Boolean {
        if (expr == null) return false
        return when (expr) {
            is UReturnExpression -> true
            is UBlockExpression -> expr.expressions.any { containsReturn(it) }
            is UIfExpression -> containsReturn(expr.thenExpression) ||
                containsReturn(expr.elseExpression)
            is UParenthesizedExpression -> containsReturn(expr.expression)
            else -> false
        }
    }
}