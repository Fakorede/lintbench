package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> =
        listOf("createCredential", "createCredentialAsync")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, call: PsiMethod) {
        if (!context.evaluator.isMemberInClass(call, "androidx.credentials.CredentialManager")) {
            return
        }

        val requestArg = node.getArgumentForParameter(1) ?: return
        val requestType = context.evaluator.getTypeClass(requestArg.getExpressionType()) ?: return
        if (!context.evaluator.extendsClass(
                requestType,
                "androidx.credentials.CreatePublicKeyCredentialRequest",
                true
            )
        ) {
            return
        }

        if (isGuardedByApi28Check(context, node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating a public key credential (Passkey) requires Android 9 (API 28) or higher. " +
                    "Add a `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` check before this call."
        )
    }

    private fun isGuardedByApi28Check(context: JavaContext, call: UCallExpression): Boolean {
        val evaluator = context.evaluator
        var current: UElement? = call.uastParent
        while (current != null) {
            if (current is UIfExpression && isApi28OrHigherCheck(current, call, evaluator)) {
                return true
            }
            current = current.uastParent
        }
        return false
    }

    private fun isApi28OrHigherCheck(ifExpr: UIfExpression, call: UElement, evaluator: JavaEvaluator): Boolean {
        if (!isInThenBranch(call, ifExpr)) {
            return false
        }

        val condition = ifExpr.condition.skipParenthesizedExprDown() as? UBinaryExpression
            ?: return false
        if (condition.operator != UastBinaryOperator.GREATER_OR_EQUALS &&
            condition.operator != UastBinaryOperator.GREATER
        ) {
            return false
        }

        val left = condition.leftOperand
        val right = condition.rightOperand
        return (isSdkIntReference(left, evaluator) && isAtLeastP(right, evaluator)) ||
                (isSdkIntReference(right, evaluator) && isAtLeastP(left, evaluator))
    }

    private fun isInThenBranch(call: UElement, ifExpr: UIfExpression): Boolean {
        val thenBranch = ifExpr.thenBranch ?: return false
        if (call == thenBranch) return true

        var current: UElement? = call
        while (current != null) {
            if (current == thenBranch) return true
            current = current.uastParent
        }
        return false
    }

    private fun isSdkIntReference(expr: UExpression, evaluator: JavaEvaluator): Boolean {
        val resolved = when (expr) {
            is USimpleNameReferenceExpression -> expr.resolve()
            is UQualifiedReferenceExpression -> expr.resolve()
            else -> null
        } ?: return false

        if (resolved !is PsiField || resolved.name != "SDK_INT") {
            return false
        }

        val containingClass = resolved.containingClass ?: return false
        return containingClass.qualifiedName == "android.os.Build.VERSION"
    }

    private fun isAtLeastP(expr: UExpression, evaluator: JavaEvaluator): Boolean {
        if (expr is ULiteralExpression) {
            val value = expr.value as? Int ?: return false
            return value >= 28
        }

        val resolved = when (expr) {
            is USimpleNameReferenceExpression -> expr.resolve()
            is UQualifiedReferenceExpression -> expr.resolve()
            else -> null
        } ?: return false

        if (resolved !is PsiField) {
            return false
        }

        val containingClass = resolved.containingClass
        if (containingClass?.qualifiedName != "android.os.Build.VERSION_CODES") {
            return false
        }

        return (resolved.computeConstantValue() as? Int)?.let { it >= 28 } == true
    }

    companion object {
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential without version check",
            explanation = """
                The Credential Manager API supports creating public key credentials (Passkeys)
                starting with Android 9 (API 28). Before calling createCredential() or
                createCredentialAsync() with a CreatePublicKeyCredentialRequest, ensure the call
                is guarded by a check such as:
                `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { ... }`
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}