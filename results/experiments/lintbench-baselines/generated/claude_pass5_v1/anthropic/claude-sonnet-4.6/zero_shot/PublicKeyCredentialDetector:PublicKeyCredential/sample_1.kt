package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) \
                starting Android 9 (API level 28) or higher. \
                Please check for the Android version before calling the method.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val CREATE_PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.PublicKeyCredential"

        private const val CREDENTIAL_MANAGER_CLASS =
            "androidx.credentials.CredentialManager"

        private val CREATE_METHOD_NAMES = setOf(
            "createCredential",
            "createCredentialAsync",
            "getCredential",
            "getCredentialAsync"
        )

        // Android 9 = API 28
        private const val MIN_API_LEVEL = 28

        private val SDK_INT_QUALIFIED_NAMES = setOf(
            "android.os.Build.VERSION.SDK_INT",
            "SDK_INT"
        )
    }

    override fun getApplicableConstructorTypes(): List<String> = listOf(
        CREATE_PUBLIC_KEY_CREDENTIAL_CLASS
    )

    override fun getApplicableMethodNames(): List<String> = listOf(
        "createCredential",
        "createCredentialAsync",
        "getCredential",
        "getCredentialAsync"
    )

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val containingClass = constructor.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName == CREATE_PUBLIC_KEY_CREDENTIAL_CLASS ||
            qualifiedName == PUBLIC_KEY_CREDENTIAL_CLASS
        ) {
            if (!isInsideVersionCheck(node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Creating public key credential requires Android 9 (API 28) or higher. " +
                        "Please check for the Android version before calling this method."
                )
            }
        }
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != CREDENTIAL_MANAGER_CLASS) return

        val methodName = method.name
        if (methodName !in CREATE_METHOD_NAMES) return

        // Check if any argument is a CreatePublicKeyCredentialRequest or PublicKeyCredential
        val hasPublicKeyArg = node.valueArguments.any { arg ->
            val argType = arg.getExpressionType()
            val canonicalText = argType?.canonicalText ?: ""
            canonicalText == CREATE_PUBLIC_KEY_CREDENTIAL_CLASS ||
                canonicalText == PUBLIC_KEY_CREDENTIAL_CLASS ||
                canonicalText.contains("PublicKeyCredential")
        }

        if (hasPublicKeyArg && !isInsideVersionCheck(node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Creating public key credential requires Android 9 (API 28) or higher. " +
                    "Please check for the Android version before calling this method."
            )
        }
    }

    private fun isInsideVersionCheck(node: UElement): Boolean {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UIfExpression) {
                if (conditionChecksVersionAtLeast28(current)) {
                    // Make sure node is in the then-branch
                    if (isInThenBranch(current, node)) {
                        return true
                    }
                }
            }
            if (current is UMethod) break
            current = current.uastParent
        }
        return false
    }

    private fun isInThenBranch(ifExpression: UIfExpression, node: UElement): Boolean {
        val thenBranch = ifExpression.thenExpression ?: return false
        var current: UElement? = node
        while (current != null) {
            if (current === thenBranch) return true
            if (current === ifExpression) break
            current = current.uastParent
        }
        return false
    }

    private fun conditionChecksVersionAtLeast28(ifExpression: UIfExpression): Boolean {
        val condition = ifExpression.condition
        return checkConditionForVersionCheck(condition)
    }

    private fun checkConditionForVersionCheck(condition: UElement): Boolean {
        if (condition is UBinaryExpression) {
            val operator = condition.operator
            val left = condition.leftOperand
            val right = condition.rightOperand

            val isSdkIntLeft = isSdkInt(left)
            val isSdkIntRight = isSdkInt(right)

            if (isSdkIntLeft) {
                val apiLevel = resolveApiLevel(right) ?: return false
                return when (operator) {
                    UastBinaryOperator.GREATER_OR_EQUALS -> apiLevel <= MIN_API_LEVEL
                    UastBinaryOperator.GREATER -> apiLevel < MIN_API_LEVEL
                    UastBinaryOperator.EQUALS -> apiLevel >= MIN_API_LEVEL
                    else -> false
                }
            } else if (isSdkIntRight) {
                val apiLevel = resolveApiLevel(left) ?: return false
                return when (operator) {
                    UastBinaryOperator.LESS_OR_EQUALS -> apiLevel <= MIN_API_LEVEL
                    UastBinaryOperator.LESS -> apiLevel < MIN_API_LEVEL
                    UastBinaryOperator.EQUALS -> apiLevel >= MIN_API_LEVEL
                    else -> false
                }
            }
        }
        return false
    }

    private fun isSdkInt(element: UElement): Boolean {
        val text = element.asSourceString().trim()
        return text.contains("SDK_INT") ||
            text == "android.os.Build.VERSION.SDK_INT"
    }

    private fun resolveApiLevel(element: UElement): Int? {
        if (element is ULiteralExpression) {
            val value = element.value
            if (value is Int) return value
            if (value is Long) return value.toInt()
        }
        if (element is UReferenceExpression) {
            val text = element.asSourceString().trim()
            // Handle Build.VERSION_CODES.P = 28
            if (text.contains("VERSION_CODES")) {
                return when {
                    text.endsWith(".P") || text == "P" -> 28
                    text.endsWith(".Q") || text == "Q" -> 29
                    text.endsWith(".R") || text == "R" -> 30
                    text.endsWith(".S") || text == "S" -> 31
                    text.endsWith(".TIRAMISU") || text == "TIRAMISU" -> 33
                    text.endsWith(".UPSIDE_DOWN_CAKE") || text == "UPSIDE_DOWN_CAKE" -> 34
                    else -> null
                }
            }
            // Try to resolve the constant value
            val resolved = (element as? UReferenceExpression)
                ?.resolve()
            if (resolved is com.intellij.psi.PsiField) {
                val initializer = resolved.initializer
                if (initializer is com.intellij.psi.PsiLiteralExpression) {
                    val v = initializer.value
                    if (v is Int) return v
                    if (v is Long) return v.toInt()
                }
            }
        }
        return null
    }
}