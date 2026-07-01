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

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CREATE_PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val PUBLIC_KEY_CREDENTIAL_CLASS =
            "androidx.credentials.PublicKeyCredential"
        private const val CREDENTIAL_MANAGER_CLASS =
            "androidx.credentials.CredentialManager"

        private val CONSTRUCTOR_CLASSES = setOf(
            CREATE_PUBLIC_KEY_CREDENTIAL_CLASS,
            PUBLIC_KEY_CREDENTIAL_CLASS
        )

        // Android 9 = API level 28
        private const val MIN_API_LEVEL = 28

        val ISSUE = Issue.create(
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
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return CONSTRUCTOR_CLASSES.toList()
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        if (!isWithinVersionCheck(node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Credential Manager API supports creating public key credential (Passkeys) " +
                        "starting Android 9 or higher. Please check for the Android version " +
                        "before calling the method."
            )
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getCredential", "createCredential", "clearCredentialState")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return

        if (qualifiedName != CREDENTIAL_MANAGER_CLASS) return

        // Check if any argument is a PublicKeyCredential-related type
        val hasPublicKeyArg = node.valueArguments.any { arg ->
            val type = arg.getExpressionType()
            val canonicalText = type?.canonicalText ?: ""
            CONSTRUCTOR_CLASSES.any { canonicalText.contains(it) }
        }

        if (!hasPublicKeyArg) return

        if (!isWithinVersionCheck(node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Credential Manager API supports creating public key credential (Passkeys) " +
                        "starting Android 9 or higher. Please check for the Android version " +
                        "before calling the method."
            )
        }
    }

    /**
     * Checks whether the given node is inside an Android version check
     * that guards API >= 28 (Android 9).
     */
    private fun isWithinVersionCheck(node: UElement): Boolean {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UIfExpression) {
                val condition = current.condition
                if (isVersionCheckForMinApi(condition, MIN_API_LEVEL)) {
                    // Make sure the node is in the then-branch, not the else-branch
                    if (isInThenBranch(node, current)) {
                        return true
                    }
                }
            }
            current = current.uastParent
        }
        return false
    }

    private fun isInThenBranch(node: UElement, ifExpression: UIfExpression): Boolean {
        val thenBranch = ifExpression.thenExpression ?: return false
        var current: UElement? = node
        while (current != null) {
            if (current == thenBranch) return true
            if (current == ifExpression) return false
            current = current.uastParent
        }
        return false
    }

    /**
     * Checks if the given condition expression is a version check
     * that ensures we're on at least [minApi].
     *
     * Handles patterns like:
     * - Build.VERSION.SDK_INT >= 28
     * - Build.VERSION.SDK_INT > 27
     * - 28 <= Build.VERSION.SDK_INT
     * - 27 < Build.VERSION.SDK_INT
     */
    private fun isVersionCheckForMinApi(condition: UElement, minApi: Int): Boolean {
        if (condition is UBinaryExpression) {
            val operator = condition.operator
            val left = condition.leftOperand
            val right = condition.rightOperand

            val leftIsSdkInt = isSdkIntReference(left)
            val rightIsSdkInt = isSdkIntReference(right)

            if (leftIsSdkInt) {
                val apiValue = resolveIntConstant(right) ?: return false
                return when (operator) {
                    UastBinaryOperator.GREATER_OR_EQUALS -> apiValue >= minApi
                    UastBinaryOperator.GREATER -> apiValue >= minApi - 1
                    UastBinaryOperator.EQUALS -> apiValue >= minApi
                    else -> false
                }
            } else if (rightIsSdkInt) {
                val apiValue = resolveIntConstant(left) ?: return false
                return when (operator) {
                    UastBinaryOperator.LESS_OR_EQUALS -> apiValue <= minApi
                    UastBinaryOperator.LESS -> apiValue <= minApi - 1
                    UastBinaryOperator.EQUALS -> apiValue >= minApi
                    else -> false
                }
            }
        }
        return false
    }

    private fun isSdkIntReference(element: UElement): Boolean {
        return when (element) {
            is UQualifiedReferenceExpression -> {
                val selector = element.selector
                if (selector is USimpleNameReferenceExpression && selector.identifier == "SDK_INT") {
                    val receiver = element.receiver
                    if (receiver is UQualifiedReferenceExpression) {
                        val outerSelector = receiver.selector
                        outerSelector is USimpleNameReferenceExpression &&
                                outerSelector.identifier == "VERSION"
                    } else if (receiver is USimpleNameReferenceExpression) {
                        receiver.identifier == "VERSION"
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            is USimpleNameReferenceExpression -> element.identifier == "SDK_INT"
            else -> false
        }
    }

    private fun resolveIntConstant(element: UElement): Int? {
        return when (element) {
            is org.jetbrains.uast.ULiteralExpression -> {
                (element.value as? Int) ?: (element.value as? Long)?.toInt()
            }
            is USimpleNameReferenceExpression -> {
                val resolved = element.resolve()
                if (resolved is com.intellij.psi.PsiField) {
                    val initializer = resolved.initializer
                    if (initializer is com.intellij.psi.PsiLiteralExpression) {
                        (initializer.value as? Int)
                    } else null
                } else null
            }
            else -> null
        }
    }
}