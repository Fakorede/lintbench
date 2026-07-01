package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PublicKeyCredentialDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = "Credential Manager supports creating public key credentials (Passkeys) " +
                    "starting with Android 9 (API level 28). Make sure the call is guarded by " +
                    "`Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` before constructing a " +
                    "`CreatePublicKeyCredentialRequest`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String> =
        listOf("androidx.credentials.CreatePublicKeyCredentialRequest")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (!isWithinVersionCheck(node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Creating public key credentials (Passkeys) requires Android 9 (API 28) or higher. " +
                        "Add a `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` check first.",
            )
        }
    }

    private fun isWithinVersionCheck(node: UElement): Boolean {
        var parent = node.uastParent
        while (parent != null && parent !is UMethod) {
            if (parent is UIfExpression) {
                if (isApi28Check(parent.condition)) {
                    return true
                }
            }
            parent = parent.uastParent
        }
        return false
    }

    private fun isApi28Check(expression: UExpression?): Boolean {
        if (expression !is UBinaryExpression) {
            return false
        }

        val operator = expression.operator
        if (operator != UastBinaryOperator.GREATER_OR_EQUALS &&
            operator != UastBinaryOperator.GREATER
        ) {
            return false
        }

        val left = expression.leftOperand
        val right = expression.rightOperand

        return (isBuildSdkInt(left) && isApi28OrHigher(right)) ||
                (isBuildSdkInt(right) && isApi28OrHigher(left))
    }

    private fun isBuildSdkInt(expression: UExpression?): Boolean {
        if (expression !is UReferenceExpression) {
            return false
        }
        val name = expression.sourcePsi?.text ?: expression.asSourceString()
        return name == "Build.VERSION.SDK_INT" || name.endsWith("VERSION.SDK_INT")
    }

    private fun isApi28OrHigher(expression: UExpression?): Boolean {
        if (expression is UReferenceExpression) {
            val name = expression.sourcePsi?.text ?: expression.asSourceString()
            return name == "Build.VERSION_CODES.P" || name.endsWith("VERSION_CODES.P")
        }

        if (expression is ULiteralExpression) {
            val value = expression.value
            if (value is Number) {
                return value.toInt() >= 28
            }
        }

        return false
    }
}