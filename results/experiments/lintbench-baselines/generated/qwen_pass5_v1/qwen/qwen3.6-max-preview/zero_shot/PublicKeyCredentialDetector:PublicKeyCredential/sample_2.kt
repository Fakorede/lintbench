package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.UastBinaryOperator.*
import java.util.EnumSet

class PublicKeyCredentialDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.CONSTRUCTOR_CALL) return

                val method = node.resolve() as? PsiMethod ?: return
                val containingClass = method.containingClass ?: return
                if (containingClass.qualifiedName != "androidx.credentials.CreatePublicKeyCredentialRequest") return

                if (!isVersionGuarded(context, node)) {
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
    }

    private fun isVersionGuarded(context: JavaContext, node: UCallExpression): Boolean {
        val evaluator = context.evaluator

        var parent: UElement? = node.uastParent
        while (parent != null) {
            if (parent is UMethod || parent is UClass) {
                val annotations = evaluator.getAllAnnotations(parent, true)
                for (ann in annotations) {
                    val qName = ann.qualifiedName
                    if (qName == "android.annotation.RequiresApi" ||
                        qName == "androidx.annotation.RequiresApi" ||
                        qName == "android.annotation.TargetApi"
                    ) {
                        val valueAttr = ann.findAttributeValue("value") ?: ann.findAttributeValue(null)
                        val apiLevel = when (valueAttr) {
                            is ULiteralExpression -> (valueAttr.value as? Number)?.toInt()
                            is UReferenceExpression -> {
                                val resolved = valueAttr.resolve()
                                if (resolved is UField) (evaluator.getFieldValue(resolved, -1) as? Number)?.toInt() else null
                            }
                            else -> null
                        }
                        if (apiLevel != null && apiLevel <= 28) return true
                    }
                }
            }
            parent = parent.uastParent
        }

        return isInsideSdkCheck(node, 28)
    }

    private fun isInsideSdkCheck(element: UElement, minSdk: Int): Boolean {
        var current: UElement? = element.uastParent
        while (current != null) {
            if (current is UIfExpression) {
                if (isSdkCheckCondition(current.condition, minSdk)) return true
            }
            current = current.uastParent
        }
        return false
    }

    private fun isSdkCheckCondition(condition: UExpression?, minSdk: Int): Boolean {
        if (condition is UBinaryExpression) {
            val op = condition.operator
            if (op == GREATER_OR_EQUAL || op == GREATER) {
                if (isSdkInt(condition.leftOperand)) {
                    val rightVal = getConstantValue(condition.rightOperand)
                    if (rightVal != null && ((op == GREATER_OR_EQUAL && rightVal >= minSdk) || (op == GREATER && rightVal >= minSdk - 1))) {
                        return true
                    }
                }
            } else if (op == LESS_OR_EQUAL || op == LESS) {
                if (isSdkInt(condition.rightOperand)) {
                    val leftVal = getConstantValue(condition.leftOperand)
                    if (leftVal != null && ((op == LESS_OR_EQUAL && leftVal >= minSdk) || (op == LESS && leftVal >= minSdk + 1))) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isSdkInt(expr: UExpression): Boolean {
        if (expr is UQualifiedReferenceExpression) {
            val receiver = expr.receiver
            val selector = expr.selector
            if (receiver is UReferenceExpression && receiver.resolvedName == "VERSION" &&
                selector is UReferenceExpression && selector.resolvedName == "SDK_INT"
            ) {
                val type = receiver.getExpressionType()
                if (type?.canonicalText == "android.os.Build.VERSION") return true
            }
        } else if (expr is UReferenceExpression && expr.resolvedName == "SDK_INT") {
            val resolved = expr.resolve()
            if (resolved is UField) {
                val containing = resolved.containingClass
                if (containing?.qualifiedName == "android.os.Build.VERSION") return true
            }
        }
        return false
    }

    private fun getConstantValue(expr: UExpression): Int? {
        return when (expr) {
            is ULiteralExpression -> (expr.value as? Number)?.toInt()
            else -> null
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "PublicKeyCredential",
            briefDescription = "Creating public key credential requires Android 9+",
            explanation = "Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. Please check for the Android version before calling the method.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                PublicKeyCredentialDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}