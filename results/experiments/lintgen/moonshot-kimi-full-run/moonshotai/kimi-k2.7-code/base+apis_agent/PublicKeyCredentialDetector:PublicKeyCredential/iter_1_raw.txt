package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("createCredential")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "androidx.credentials.CredentialManager")) {
            return
        }

        if (context.mainProject.minSdk >= MIN_API) {
            return
        }

        if (!hasPublicKeyCredentialArgument(context, node)) {
            return
        }

        if (isWithinVersionCheck(node)) {
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
        return node.valueArguments.any { arg ->
            val type = arg.getExpressionType() ?: return@any false
            val clazz = evaluator.getTypeClass(type)
            clazz?.qualifiedName == "androidx.credentials.CreatePublicKeyCredentialRequest"
        }
    }

    private fun isWithinVersionCheck(node: UCallExpression): Boolean {
        var element: UElement? = node
        while (element != null) {
            val ifExpr = element.getParentOfType<UIfExpression>(true) ?: break
            if (isValidVersionCheck(ifExpr.condition)) {
                return true
            }
            element = ifExpr.uastParent
        }
        return false
    }

    private fun isValidVersionCheck(condition: UExpression?): Boolean {
        if (condition == null) return false
        if (condition !is UBinaryExpression) return false

        return when (condition.operator) {
            UastBinaryOperator.GREATER_OR_EQUALS,
            UastBinaryOperator.GREATER -> {
                isSdkIntAtLeast(condition.leftOperand, condition.rightOperand, condition.operator)
            }
            UastBinaryOperator.LOGICAL_AND -> {
                isValidVersionCheck(condition.leftOperand) ||
                        isValidVersionCheck(condition.rightOperand)
            }
            UastBinaryOperator.LOGICAL_OR -> {
                isValidVersionCheck(condition.leftOperand) &&
                        isValidVersionCheck(condition.rightOperand)
            }
            else -> false
        }
    }

    private fun isSdkIntAtLeast(
        left: UExpression?,
        right: UExpression?,
        operator: UastBinaryOperator
    ): Boolean {
        val leftIsSdkInt = isSdkIntReference(left)
        val rightIsSdkInt = isSdkIntReference(right)
        if (!leftIsSdkInt && !rightIsSdkInt) return false

        val level = if (leftIsSdkInt) getApiLevel(right) else getApiLevel(left)
        if (level == null) return false

        return if (operator == UastBinaryOperator.GREATER_OR_EQUALS) {
            level >= MIN_API
        } else {
            level >= MIN_API - 1
        }
    }

    private fun isSdkIntReference(expression: UExpression?): Boolean {
        val resolved = (expression as? UReferenceExpression)?.resolve()
        return resolved is PsiField
                && resolved.containingClass?.qualifiedName == "android.os.Build.VERSION"
                && resolved.name == "SDK_INT"
    }

    private fun getApiLevel(expression: UExpression?): Int? {
        if (expression is ULiteralExpression) {
            val value = expression.value
            if (value is Number) return value.toInt()
        }

        val resolved = (expression as? UReferenceExpression)?.resolve()
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

        val ISSUE = Issue.create(
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