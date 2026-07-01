package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UCallableReferenceExpression

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) \
                starting Android 9 (API level 28) or higher. Please check for the Android \
                version before calling the method.
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

        private const val MIN_SDK_VERSION = 28 // Android 9 (Pie)
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
        if (containingClass == CREATE_PUBLIC_KEY_CREDENTIAL_CLASS ||
            containingClass == PUBLIC_KEY_CREDENTIAL_CLASS
        ) {
            if (!isSdkVersionChecked(node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Creating public key credential requires Android 9 (API level 28) or higher. " +
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
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass == CREDENTIAL_MANAGER_CLASS) {
            val methodName = method.name
            if (methodName == "createCredential" || methodName == "getCredential" ||
                methodName == "clearCredentialState"
            ) {
                // Check if any argument is a CreatePublicKeyCredentialRequest or PublicKeyCredential
                val hasPublicKeyArg = node.valueArguments.any { arg ->
                    val argType = arg.getExpressionType()?.canonicalText ?: ""
                    argType == CREATE_PUBLIC_KEY_CREDENTIAL_CLASS ||
                            argType == PUBLIC_KEY_CREDENTIAL_CLASS
                }
                if (hasPublicKeyArg && !isSdkVersionChecked(node)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Creating public key credential requires Android 9 (API level 28) or higher. " +
                                "Please check for the Android version before calling this method."
                    )
                }
            }
        }
    }

    /**
     * Checks whether the given node is inside an SDK version check
     * (e.g., `if (Build.VERSION.SDK_INT >= 28)` or `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)`).
     */
    private fun isSdkVersionChecked(node: UElement): Boolean {
        var current: UElement? = node.uastParent
        while (current != null) {
            if (current is UIfExpression) {
                val condition = current.condition
                if (isSdkVersionCondition(condition)) {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }

    private fun isSdkVersionCondition(condition: UElement): Boolean {
        if (condition is UBinaryExpression) {
            val operator = condition.operator
            val left = condition.leftOperand
            val right = condition.rightOperand

            val isSdkIntRef = isSdkIntReference(left) || isSdkIntReference(right)
            if (!isSdkIntRef) return false

            // Check that the comparison is >= MIN_SDK_VERSION or > MIN_SDK_VERSION - 1
            val versionValue = if (isSdkIntReference(left)) {
                getVersionValue(right)
            } else {
                getVersionValue(left)
            }

            if (versionValue != null) {
                return when {
                    isSdkIntReference(left) && (
                            operator == UastBinaryOperator.GREATER_OR_EQUALS && versionValue >= MIN_SDK_VERSION ||
                                    operator == UastBinaryOperator.GREATER && versionValue >= MIN_SDK_VERSION - 1 ||
                                    operator == UastBinaryOperator.EQUALS && versionValue >= MIN_SDK_VERSION
                            ) -> true
                    isSdkIntReference(right) && (
                            operator == UastBinaryOperator.LESS_OR_EQUALS && versionValue >= MIN_SDK_VERSION ||
                                    operator == UastBinaryOperator.LESS && versionValue >= MIN_SDK_VERSION - 1 ||
                                    operator == UastBinaryOperator.EQUALS && versionValue >= MIN_SDK_VERSION
                            ) -> true
                    else -> false
                }
            }
        }
        return false
    }

    private fun isSdkIntReference(element: UElement): Boolean {
        val text = element.asSourceString()
        return text.contains("SDK_INT") && text.contains("VERSION")
    }

    private fun getVersionValue(element: UElement): Int? {
        if (element is ULiteralExpression) {
            val value = element.value
            if (value is Int) return value
            if (value is Long) return value.toInt()
        }
        // Handle Build.VERSION_CODES.P etc.
        val text = element.asSourceString()
        return when {
            text.contains("VERSION_CODES") -> {
                when {
                    text.endsWith(".P") || text.endsWith("P") -> 28
                    text.endsWith(".Q") || text.endsWith("Q") -> 29
                    text.endsWith(".R") || text.endsWith("R") -> 30
                    text.endsWith(".S") || text.endsWith("S") -> 31
                    text.endsWith(".S_V2") -> 32
                    text.endsWith(".TIRAMISU") -> 33
                    text.endsWith(".UPSIDE_DOWN_CAKE") -> 34
                    text.endsWith(".O") || text.endsWith("O") -> 26
                    text.endsWith(".O_MR1") -> 27
                    text.endsWith(".N") || text.endsWith("N") -> 24
                    text.endsWith(".N_MR1") -> 25
                    text.endsWith(".M") || text.endsWith("M") -> 23
                    text.endsWith(".LOLLIPOP") -> 21
                    text.endsWith(".LOLLIPOP_MR1") -> 22
                    text.endsWith(".KITKAT") -> 19
                    else -> null
                }
            }
            else -> null
        }
    }
}