package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("createCredential")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "androidx.credentials.CredentialManager")) {
            return
        }

        if (!hasPublicKeyCredentialArgument(context, node)) {
            return
        }

        if (isWithinVersionCheck(node, MIN_API)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating a public key credential is only supported on Android 9 (API 28) and higher. " +
                    "Check `Build.VERSION.SDK_INT` before calling `createCredential()`."
        )
    }

    private fun hasPublicKeyCredentialArgument(context: JavaContext, node: UCallExpression): Boolean {
        val evaluator = context.evaluator
        for (arg in node.valueArguments) {
            val argType = arg.getExpressionType() ?: continue
            val argClass = evaluator.getTypeClass(argType)
            if (argClass?.qualifiedName == "androidx.credentials.CreatePublicKeyCredentialRequest") {
                return true
            }
        }
        return false
    }

    private fun isWithinVersionCheck(node: UCallExpression, minApi: Int): Boolean {
        var element: UElement? = node
        while (element != null) {
            val ifExpr = element.getParentOfType<UIfExpression>(true) ?: break
            if (isValidVersionCheck(ifExpr.condition, minApi)) {
                return true
            }
            element = ifExpr.uastParent
        }
        return false
    }

    private fun isValidVersionCheck(expr: UExpression?, minApi: Int): Boolean {
        if (expr == null) return false
        if (expr is UBinaryExpression) {
            val op = expr.operator
            if (op == UastBinaryOperator.LOGICAL_AND) {
                return isValidVersionCheck(expr.leftOperand, minApi)
                        || isValidVersionCheck(expr.rightOperand, minApi)
            }
            if (op == UastBinaryOperator.GREATER_OR_EQUALS || op == UastBinaryOperator.GREATER) {
                val allowEqual = op == UastBinaryOperator.GREATER_OR_EQUALS
                return isSdkIntAtLeast(expr.leftOperand, expr.rightOperand, minApi, allowEqual)
            }
        }
        return false
    }

    private fun isSdkIntAtLeast(
        left: UExpression?,
        right: UExpression?,
        minApi: Int,
        allowEqual: Boolean
    ): Boolean {
        if (isSdkIntReference(left)) {
            val level = getApiLevel(right)
            return level != null && if (allowEqual) level >= minApi else level > minApi
        }
        if (isSdkIntReference(right)) {
            val level = getApiLevel(left)
            return level != null && if (allowEqual) level >= minApi else level > minApi
        }
        return false
    }

    private fun isSdkIntReference(expr: UExpression?): Boolean {
        val resolved = when (expr) {
            is USimpleNameReferenceExpression -> expr.resolve()
            is UQualifiedReferenceExpression -> expr.resolve()
            else -> null
        }
        return resolved is PsiField
                && resolved.containingClass?.qualifiedName == "android.os.Build.VERSION"
                && resolved.name == "SDK_INT"
    }

    private fun getApiLevel(expr: UExpression?): Int? {
        if (expr is ULiteralExpression) {
            val value = expr.value
            if (value is Number) return value.toInt()
        }
        val resolved = when (expr) {
            is USimpleNameReferenceExpression -> expr.resolve()
            is UQualifiedReferenceExpression -> expr.resolve()
            else -> null
        }
        return (resolved as? PsiField)?.name?.let { API_LEVELS[it] }
    }

    companion object {
        private const val MIN_API = 28

        private val API_LEVELS = mapOf(
            "P" to 28,
            "Q" to 29,
            "R" to 30,
            "S" to 31,
            "S_V2" to 32,
            "TIRAMISU" to 33,
            "UPSIDE_DOWN_CAKE" to 34,
            "VANILLA_ICE_CREAM" to 35
        )

        private val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Public key credential creation requires Android 9 or higher",
            explanation = """
                The Credential Manager API supports creating public key credentials (passkeys) only on
                Android 9 (API 28) and higher. You must check `Build.VERSION.SDK_INT` before calling
                `CredentialManager.createCredential(...)` with a `CreatePublicKeyCredentialRequest`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}