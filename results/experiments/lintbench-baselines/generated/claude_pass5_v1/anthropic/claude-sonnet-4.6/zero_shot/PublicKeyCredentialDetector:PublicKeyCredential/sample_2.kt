package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.UastBinaryOperator

class PublicKeyCredentialDetector : Detector(), Detector.UastScanner {

    companion object {
        private const val CREATE_PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.PublicKeyCredential"
        private const val CREDENTIAL_MANAGER_CLASS =
            "androidx.credentials.CredentialManager"

        private val TARGET_CONSTRUCTORS = setOf(
            CREATE_PUBLIC_KEY_CREDENTIAL_CLASS,
            PUBLIC_KEY_CREDENTIAL_CLASS
        )

        val ISSUE: Issue = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) \
                starting Android 9 or higher. Please check for the Android version before \
                calling the method.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        // Android 9 is API level 28
        private const val MIN_REQUIRED_API = 28

        private val SDK_INT_REFERENCES = setOf("SDK_INT", "VERSION.SDK_INT")
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf(
            CREATE_PUBLIC_KEY_CREDENTIAL_CLASS,
            PUBLIC_KEY_CREDENTIAL_CLASS
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "createCredential",
            "getCredential",
            "clearCredentialState"
        )
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val containingClass = constructor.containingClass?.qualifiedName ?: return
        if (containingClass in TARGET_CONSTRUCTORS) {
            if (!isInsideApiVersionCheck(node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Creating public key credential requires Android 9 (API level 28) or higher. " +
                            "Please check for the Android version before calling this."
                )
            }
        }
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass == CREDENTIAL_MANAGER_CLASS) {
            if (!isInsideApiVersionCheck(node)) {
                // Check if any argument is a PublicKeyCredential-related type
                val hasPublicKeyArg = node.valueArguments.any { arg ->
                    val type = arg.getExpressionType()?.canonicalText ?: ""
                    type.contains("PublicKey") || type.contains("Passkey")
                }
                if (hasPublicKeyArg) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Creating public key credential requires Android 9 (API level 28) or higher. " +
                                "Please check for the Android version before calling this."
                    )
                }
            }
        }
    }

    private fun isInsideApiVersionCheck(node: UElement): Boolean {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UIfExpression) {
                val condition = current.condition
                if (containsApiVersionCheck(condition)) {
                    return true
                }
            }
            if (current is UMethod) {
                // Check if the method itself has an API annotation
                if (hasRequiresApiAnnotation(current)) {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }

    private fun hasRequiresApiAnnotation(method: UMethod): Boolean {
        val annotations = method.uAnnotations
        for (annotation in annotations) {
            val name = annotation.qualifiedName ?: continue
            if (name == "androidx.annotation.RequiresApi" ||
                name == "android.annotation.RequiresApi" ||
                name == "androidx.annotation.TargetApi" ||
                name == "android.annotation.TargetApi"
            ) {
                val value = annotation.findAttributeValue("value")
                    ?: annotation.findAttributeValue(null)
                val apiLevel = value?.evaluate() as? Int ?: continue
                if (apiLevel >= MIN_REQUIRED_API) {
                    return true
                }
            }
        }
        return false
    }

    private fun containsApiVersionCheck(condition: UElement?): Boolean {
        if (condition == null) return false

        if (condition is UBinaryExpression) {
            val operator = condition.operator
            val left = condition.leftOperand
            val right = condition.rightOperand

            val isComparisonOperator = operator == UastBinaryOperator.GREATER ||
                    operator == UastBinaryOperator.GREATER_OR_EQUALS ||
                    operator == UastBinaryOperator.LESS ||
                    operator == UastBinaryOperator.LESS_OR_EQUALS ||
                    operator == UastBinaryOperator.EQUALS ||
                    operator == UastBinaryOperator.NOT_EQUALS

            if (isComparisonOperator) {
                val leftText = left.asSourceString()
                val rightText = right.asSourceString()

                val leftIsSdkInt = leftText.contains("SDK_INT")
                val rightIsSdkInt = rightText.contains("SDK_INT")

                if (leftIsSdkInt || rightIsSdkInt) {
                    // Try to evaluate the API level being compared
                    val apiLevelValue = if (leftIsSdkInt) {
                        evaluateApiLevel(right)
                    } else {
                        evaluateApiLevel(left)
                    }

                    if (apiLevelValue != null && apiLevelValue >= MIN_REQUIRED_API) {
                        return true
                    }

                    // If we can't evaluate, assume it might be checking for the right version
                    if (apiLevelValue == null && (leftIsSdkInt || rightIsSdkInt)) {
                        return true
                    }
                }
            }

            // Recurse into logical AND/OR expressions
            if (operator == UastBinaryOperator.LOGICAL_AND ||
                operator == UastBinaryOperator.LOGICAL_OR
            ) {
                return containsApiVersionCheck(left) || containsApiVersionCheck(right)
            }
        }

        return false
    }

    private fun evaluateApiLevel(element: UElement): Int? {
        // Try direct evaluation
        if (element is UCallExpression || element is UQualifiedReferenceExpression ||
            element is USimpleNameReferenceExpression
        ) {
            val text = element.asSourceString()
            // Check for Build.VERSION_CODES constants
            return when {
                text.contains("P") && (text.contains("VERSION_CODES") || text.length == 1) -> 28
                text.contains("Q") && (text.contains("VERSION_CODES") || text.length == 1) -> 29
                text.contains("R") && (text.contains("VERSION_CODES") || text.length == 1) -> 30
                text.contains("S") && (text.contains("VERSION_CODES") || text.length == 1) -> 31
                text.contains("TIRAMISU") -> 33
                text.contains("UPSIDE_DOWN_CAKE") -> 34
                else -> null
            }
        }

        // Try to evaluate as integer literal
        val evaluated = (element as? org.jetbrains.uast.ULiteralExpression)?.evaluate()
        return evaluated as? Int
    }
}