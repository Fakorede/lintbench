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
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val MIN_API = 28

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credentials requires Android 9+",
            explanation = """
                Credential Manager supports creating public key credentials (Passkeys) on Android 9 \
                (API level 28) and higher. Make sure the call is guarded by a version check such as \
                `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)`.
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

    override fun getApplicableMethodNames(): List<String> =
        listOf("createCredential", "createCredentialAsync")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        val method = node.resolve() ?: return
        if (!context.evaluator.isMemberInClass(method, "androidx.credentials.CredentialManager")) {
            return
        }

        val hasPublicKeyRequest = node.valueArguments.any { arg ->
            arg.getExpressionType()?.canonicalText?.contains("CreatePublicKeyCredentialRequest") == true
        }
        if (!hasPublicKeyRequest) {
            return
        }

        if (isWithinVersionCheck(context, node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating public key credentials requires Android 9 (API 28) or higher; add a Build.VERSION.SDK_INT >= Build.VERSION_CODES.P check"
        )
    }

    private fun isWithinVersionCheck(context: JavaContext, call: UCallExpression): Boolean {
        var element: UElement? = call
        while (element != null) {
            val ifExpr = element.getParentOfType(UIfExpression::class.java, true) ?: break
            if (conditionChecksMinApi(context, ifExpr.condition)) {
                return true
            }
            element = ifExpr.uastParent
        }

        val containingMethod = call.getContainingUMethod()
        if (containingMethod != null && hasMinApiAnnotation(context, containingMethod)) {
            return true
        }

        val containingClass = call.getContainingUClass()
        if (containingClass != null && hasMinApiAnnotation(context, containingClass)) {
            return true
        }

        return false
    }

    private fun conditionChecksMinApi(context: JavaContext, condition: UExpression?): Boolean {
        if (condition !is UBinaryExpression) return false
        if (condition.operator !in ALLOWED_OPERATORS) return false

        val left = condition.leftOperand
        val right = condition.rightOperand

        return if (isSdkIntReference(left)) {
            apiLevel(context, right)?.let { it >= MIN_API } == true
        } else if (isSdkIntReference(right)) {
            apiLevel(context, left)?.let { it >= MIN_API } == true
        } else {
            false
        }
    }

    private fun isSdkIntReference(expression: UExpression?): Boolean {
        return expression is UReferenceExpression && expression.resolvedName == "SDK_INT"
    }

    private fun apiLevel(context: JavaContext, expression: UExpression?): Int? {
        if (expression == null) return null
        val constant = ConstantEvaluator.evaluate(context, expression) as? Int
        if (constant != null) return constant
        return versionCodeNameToApi(expression)
    }

    private fun versionCodeNameToApi(expression: UExpression?): Int? {
        if (expression !is UReferenceExpression) return null
        return when (expression.resolvedName) {
            "P" -> 28
            "Q" -> 29
            "R" -> 30
            "S" -> 31
            "S_V2" -> 32
            "TIRAMISU" -> 33
            "UPSIDE_DOWN_CAKE" -> 34
            "VANILLA_ICE_CREAM" -> 35
            else -> null
        }
    }

    private fun hasMinApiAnnotation(context: JavaContext, element: UAnnotated): Boolean {
        for (annotation in element.uAnnotations) {
            val fqcn = annotation.qualifiedName ?: continue
            if (!fqcn.endsWith(".RequiresApi") && !fqcn.endsWith(".TargetApi")) continue

            val value = annotation.findAttributeValue("value")
                ?: annotation.findAttributeValue("api")
                ?: continue

            val api = apiLevel(context, value) ?: continue
            if (api >= MIN_API) return true
        }
        return false
    }

    private val ALLOWED_OPERATORS = setOf(
        UastBinaryOperator.GREATER_OR_EQUALS,
        UastBinaryOperator.GREATER,
        UastBinaryOperator.EQUALS,
        UastBinaryOperator.IDENTITY_EQUALS
    )
}