package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.UastBinaryOperator.GREATER
import org.jetbrains.uast.UastBinaryOperator.GREATER_OR_EQUAL

class PublicKeyCredentialDetector : Detector(), Detector.UastScanner {

    companion object {
        val ISSUE = Issue.create(
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
        private const val ANDROIDX_CREDENTIAL_MANAGER = "androidx.credentials.CredentialManager"
        private const val PLATFORM_CREDENTIAL_MANAGER = "android.credentials.CredentialManager"
        private const val ANDROIDX_CREATE_REQUEST = "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val PLATFORM_CREATE_REQUEST = "android.credentials.CreatePublicKeyCredentialRequest"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (isTargetCall(node) && !isProtectedBySdkCheck(node, context)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Creating public key credential requires checking for Android 9 (API 28) or higher"
                    )
                }
            }
        }
    }

    private fun isTargetCall(node: UCallExpression): Boolean {
        val method = node.resolve() ?: return false
        val className = method.containingClass?.qualifiedName ?: return false
        val methodName = method.name

        return (methodName == "createCredential" &&
                (className == ANDROIDX_CREDENTIAL_MANAGER || className == PLATFORM_CREDENTIAL_MANAGER)) ||
               (methodName == "<init>" &&
                (className == ANDROIDX_CREATE_REQUEST || className == PLATFORM_CREATE_REQUEST))
    }

    private fun isProtectedBySdkCheck(node: UElement, context: JavaContext): Boolean {
        val evaluator = context.evaluator

        val method = node.getContainingUastMethod()
        if (method != null && hasRequiresApi(evaluator, method)) return true

        val cls = node.getContainingUClass()
        if (cls != null && hasRequiresApi(evaluator, cls)) return true

        var current: UElement? = node.uastParent
        while (current != null && current !is UMethod) {
            if (current is UIfExpression) {
                if (isSdkCheckCondition(current.condition)) {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }

    private fun hasRequiresApi(evaluator: JavaEvaluator, element: UElement): Boolean {
        val annotation = evaluator.getAnnotation(element, "androidx.annotation.RequiresApi")
            ?: evaluator.getAnnotation(element, "android.annotation.RequiresApi")
            ?: return false
        val value = annotation.findAttributeValue("value") ?: annotation.findAttributeValue("api")
        val apiLevel = (value as? ULiteralExpression)?.value as? Int ?: 0
        return apiLevel >= MIN_SDK
    }

    private fun isSdkCheckCondition(condition: UExpression?): Boolean {
        if (condition !is UBinaryExpression) return false

        val left = condition.leftOperand
        val right = condition.rightOperand

        return when (condition.operator) {
            GREATER_OR_EQUAL -> {
                (isSdkIntReference(left) && isIntLiteral(right, MIN_SDK)) ||
                (isSdkIntReference(right) && isIntLiteral(left, MIN_SDK))
            }
            GREATER -> {
                (isSdkIntReference(left) && isIntLiteral(right, MIN_SDK - 1)) ||
                (isSdkIntReference(right) && isIntLiteral(left, MIN_SDK - 1))
            }
            else -> false
        }
    }

    private fun isSdkIntReference(expr: UExpression): Boolean {
        if (expr is UReferenceExpression) {
            val resolved = expr.resolve()
            if (resolved?.containingClass?.qualifiedName == "android.os.Build.VERSION" && resolved.name == "SDK_INT") {
                return true
            }
        }
        val source = expr.asSourceString()
        return source == "Build.VERSION.SDK_INT" || source == "android.os.Build.VERSION.SDK_INT"
    }

    private fun isIntLiteral(expr: UExpression, value: Int): Boolean {
        return (expr as? ULiteralExpression)?.value == value
    }
}