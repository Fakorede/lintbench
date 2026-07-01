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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val CREATE_PUBLIC_KEY_REQUEST =
            "androidx.credentials.CreatePublicKeyCredentialRequest"
        private const val BUILD_VERSION_CLASS = "android.os.Build\$VERSION"
        private const val VERSION_CODES_CLASS = "android.os.Build\$VERSION_CODES"
        private const val MIN_PASSKEY_API = 28

        private val IMPLEMENTATION = Implementation(
            PublicKeyCredentialDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential",
            explanation = """
                Credential Manager supports creating public key credentials (Passkeys)
                only on Android 9 (API 28) and higher. Make sure you guard calls that
                construct a `CreatePublicKeyCredentialRequest` with a runtime version
                check, for example:

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val request = CreatePublicKeyCredentialRequest(...)
                    }
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? =
        listOf(CREATE_PUBLIC_KEY_REQUEST)

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        if (context.project.minSdk >= MIN_PASSKEY_API) {
            return
        }

        if (isWithinSupportedVersionGuard(node)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating a public key credential (Passkey) requires Android 9 (API 28) or higher. " +
                "Add a runtime version check such as `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` " +
                "before constructing CreatePublicKeyCredentialRequest.",
        )
    }

    private fun isWithinSupportedVersionGuard(node: UCallExpression): Boolean {
        var ifExpr: UIfExpression? = node.getParentOfType(UIfExpression::class.java, true)
        while (ifExpr != null) {
            if (isGuardedBy(ifExpr, node)) {
                return true
            }
            ifExpr = ifExpr.getParentOfType(UIfExpression::class.java, true)
        }
        return false
    }

    private fun isGuardedBy(ifExpr: UIfExpression, node: UCallExpression): Boolean {
        val condition = ifExpr.condition
        return when {
            isSupportedVersionCheck(condition) -> isInBranch(ifExpr.thenExpression, node)
            isUnsupportedVersionCheck(condition) -> isInBranch(ifExpr.elseExpression, node)
            else -> false
        }
    }

    private fun isInBranch(branch: UExpression?, node: UCallExpression): Boolean {
        if (branch == null) return false
        var current: UElement? = node
        while (current != null) {
            if (current == branch) return true
            current = current.uastParent
        }
        return false
    }

    private fun isSupportedVersionCheck(condition: UExpression): Boolean {
        val binary = condition as? UBinaryExpression ?: return false
        if (!isBuildVersionSdkInt(binary.leftOperand)) return false
        val value = getVersionCodeConstant(binary.rightOperand) ?: return false
        return when (binary.operator) {
            UastBinaryOperator.GREATER_OR_EQUALS -> value >= MIN_PASSKEY_API
            UastBinaryOperator.GREATER -> value >= MIN_PASSKEY_API - 1
            else -> false
        }
    }

    private fun isUnsupportedVersionCheck(condition: UExpression): Boolean {
        val binary = condition as? UBinaryExpression ?: return false
        if (!isBuildVersionSdkInt(binary.leftOperand)) return false
        val value = getVersionCodeConstant(binary.rightOperand) ?: return false
        return when (binary.operator) {
            UastBinaryOperator.LESS -> value >= MIN_PASSKEY_API
            UastBinaryOperator.LESS_OR_EQUALS -> value >= MIN_PASSKEY_API - 1
            else -> false
        }
    }

    private fun isBuildVersionSdkInt(expression: UExpression): Boolean {
        val qualified = expression as? UQualifiedReferenceExpression ?: return false
        val selector = qualified.selector as? USimpleNameReferenceExpression ?: return false
        if (selector.identifier != "SDK_INT") return false
        val field = selector.resolve() as? PsiField ?: return false
        return field.containingClass?.qualifiedName == BUILD_VERSION_CLASS
    }

    private fun getVersionCodeConstant(expression: UExpression): Int? {
        if (expression is ULiteralExpression) {
            return (expression.value as? Number)?.toInt()
        }
        val qualified = expression as? UQualifiedReferenceExpression ?: return null
        val selector = qualified.selector as? USimpleNameReferenceExpression ?: return null
        val field = selector.resolve() as? PsiField ?: return null
        if (field.containingClass?.qualifiedName != VERSION_CODES_CLASS) return null
        return (field.computeConstantValue() as? Number)?.toInt()
    }
}