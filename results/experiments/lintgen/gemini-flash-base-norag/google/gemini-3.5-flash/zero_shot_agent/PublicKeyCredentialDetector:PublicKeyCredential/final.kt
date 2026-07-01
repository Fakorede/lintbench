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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. \
                Please check for the Android version before calling the method.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableConstructorTypes(): List<String> {
        return listOf("androidx.credentials.CreatePublicKeyCredentialRequest")
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        val minSdkVersion = context.project.minSdkVersion.apiLevel
        if (minSdkVersion < 28) {
            if (!isVersionChecked(node, 28)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Creating public key credential (Passkeys) requires Android 9 (API 28) or higher"
                )
            }
        }
    }

    private fun isVersionChecked(element: UElement, targetApi: Int): Boolean {
        var current: UElement? = element
        while (current != null) {
            val ifExpr = current.getParentOfType<UIfExpression>(UIfExpression::class.java, true) ?: break
            val condition = ifExpr.condition
            if (checksSdkVersion(condition, targetApi)) {
                return true
            }
            current = ifExpr
        }
        return false
    }

    private fun checksSdkVersion(condition: UElement, targetApi: Int): Boolean {
        val conditionStr = condition.asSourceString()
        if (conditionStr.contains("SDK_INT") && conditionStr.contains("$targetApi")) {
            return true
        }
        if (condition is UBinaryExpression) {
            val left = condition.leftOperand
            val right = condition.rightOperand
            if (isSdkVersionReference(left) && isApiLevelLiteral(right, targetApi)) {
                return true
            }
            if (isSdkVersionReference(right) && isApiLevelLiteral(left, targetApi)) {
                return true
            }
        }
        return false
    }

    private fun isSdkVersionReference(element: UElement): Boolean {
        if (element is UReferenceExpression) {
            val resolved = element.resolve()
            if (resolved is PsiMethod) {
                return resolved.name == "getSDK_INT" || resolved.name == "SDK_INT"
            }
            val name = element.resolvedName ?: element.asSourceString()
            return name == "SDK_INT"
        }
        return element.asSourceString().contains("SDK_INT")
    }

    private fun isApiLevelLiteral(element: UElement, targetApi: Int): Boolean {
        if (element is ULiteralExpression) {
            val value = element.value
            if (value is Int) {
                return value >= targetApi
            }
        }
        val source = element.asSourceString()
        return source.contains("$targetApi") || source.contains("P") || source.contains("VERSION_CODES.P")
    }
}