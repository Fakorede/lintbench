package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import org.jetbrains.uast.UastBinaryOperator.CONDITIONAL_AND
import org.jetbrains.uast.UastBinaryOperator.GREATER_OR_EQUAL

class PublicKeyCredentialDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val qualifiedName = method.containingClass?.qualifiedName ?: return

                if (qualifiedName == "androidx.credentials.CreatePublicKeyCredentialRequest") {
                    if (!isGuardedByApiLevel28(node)) {
                        context.report(
                            ISSUE,
                            context.getLocation(node),
                            "Creating public key credential requires Android 9 (API 28) or higher. " +
                                    "Wrap this call in a version check."
                        )
                    }
                }
            }
        }
    }

    private fun isGuardedByApiLevel28(element: UElement): Boolean {
        var current: UElement? = element.uastParent
        while (current != null) {
            if (current is UIfExpression) {
                if (checksApiLevel28OrHigher(current.condition)) return true
            }
            if (current is UMethod || current is UClass || current is ULambdaExpression) break
            current = current.uastParent
        }
        return false
    }

    private fun checksApiLevel28OrHigher(condition: UExpression?): Boolean {
        if (condition is UBinaryExpression) {
            when (condition.operator) {
                GREATER_OR_EQUAL -> {
                    if (isSdkInt(condition.leftOperand) && isApiLevel28(condition.rightOperand)) return true
                    if (isSdkInt(condition.rightOperand) && isApiLevel28(condition.leftOperand)) return true
                }
                CONDITIONAL_AND -> {
                    return checksApiLevel28OrHigher(condition.leftOperand) || checksApiLevel28OrHigher(condition.rightOperand)
                }
                else -> {}
            }
        }
        return false
    }

    private fun isSdkInt(expr: UExpression): Boolean {
        val ref = expr as? UQualifiedReferenceExpression ?: return false
        return ref.receiver.asSourceString() == "Build.VERSION" && ref.selector.asSourceString() == "SDK_INT"
    }

    private fun isApiLevel28(expr: UExpression): Boolean {
        if (expr is ULiteralExpression && expr.value == 28) return true
        val ref = expr as? UQualifiedReferenceExpression ?: return false
        val selector = ref.selector.asSourceString()
        val receiver = ref.receiver.asSourceString()
        return selector == "P" && (receiver == "Build.VERSION_CODES" || receiver.endsWith(".VERSION_CODES"))
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
    }
}