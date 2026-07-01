package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

private const val MIN_API = 28 // Android 9 (P)
private const val REQUEST_CLASS = "androidx.credentials.CreatePublicKeyCredentialRequest"

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
            explanation = """
                The Credential Manager API supports creating public key credentials (Passkeys)
                starting with Android 9 (API level 28). On earlier platforms this call can fail
                or behave unexpectedly.

                Make sure the call is guarded by a runtime Android version check, for example:
                `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { ... }`

                Alternatively, you can annotate the calling method or class with
                `@RequiresApi(Build.VERSION_CODES.P)` or `@TargetApi(Build.VERSION_CODES.P)`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableConstructorTypes(): List<String>? =
        listOf(REQUEST_CLASS)

    override fun visitConstructor(
        context: JavaContext, node: UCallExpression, constructor: PsiMethod,
    ) {
        if (context.project.minSdkVersion.apiLevel >= MIN_API) return
        if (isGuardedByVersionCheck(node) || hasSufficientApiAnnotation(node)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Creating a public key credential is only supported on Android 9 (API level 28) and higher; " +
                "check the Android version before this call.",
        )
    }

    private fun isGuardedByVersionCheck(node: UCallExpression): Boolean {
        var ifExpr: UIfExpression? = node.getParentOfType(UIfExpression::class.java, true)
        while (ifExpr != null) {
            if (isSdkVersionAtLeastP(ifExpr.condition)) return true
            ifExpr = ifExpr.getParentOfType(UIfExpression::class.java, true)
        }
        return false
    }

    private fun isSdkVersionAtLeastP(expression: UExpression): Boolean {
        if (expression is UBinaryExpression) {
            if (expression.operator == UastBinaryOperator.BOOLEAN_AND) {
                return isSdkVersionAtLeastP(expression.leftOperand) ||
                    isSdkVersionAtLeastP(expression.rightOperand)
            }
            if (expression.operator == UastBinaryOperator.GREATER_OR_EQUALS ||
                expression.operator == UastBinaryOperator.GREATER
            ) {
                val left = expression.leftOperand.sourcePsi?.text ?: ""
                val right = expression.rightOperand.sourcePsi?.text ?: ""
                if (left.contains("Build.VERSION.SDK_INT") &&
                    (right.contains("VERSION_CODES.P") || right == "28")) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasSufficientApiAnnotation(node: UCallExpression): Boolean {
        var current: UElement? = node
        while (current != null) {
            val annotations = when (current) {
                is UMethod -> current.uAnnotations
                is UClass -> current.uAnnotations
                else -> emptyList()
            }
            for (annotation in annotations) {
                if (isSufficientApiAnnotation(annotation)) return true
            }
            current = current.uastParent
        }
        return false
    }

    private fun isSufficientApiAnnotation(annotation: UAnnotation): Boolean {
        val name = annotation.qualifiedName ?: return false
        if (name != "androidx.annotation.RequiresApi" &&
            name != "androidx.annotation.TargetApi" &&
            name != "android.annotation.TargetApi"
        ) {
            return false
        }
        for (attribute: UNamedExpression in annotation.attributeValues) {
            val text = attribute.expression.sourcePsi?.text ?: continue
            if (text.contains("VERSION_CODES.P") || text == "28") return true
        }
        return false
    }
}