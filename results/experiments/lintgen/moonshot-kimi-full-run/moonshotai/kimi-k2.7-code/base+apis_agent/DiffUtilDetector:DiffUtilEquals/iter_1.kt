package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil equality check",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. Using identity
                equality (`==` in Java or `===` in Kotlin) or calling `equals` on a type that
                does not override it can produce incorrect diff results and visual artifacts.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val DIFF_UTIL_CLASSES = listOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
        private const val METHOD_NAME = "areContentsTheSame"
        private const val JAVA_OBJECT = "java.lang.Object"
        private const val KOTLIN_ANY = "kotlin.Any"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UMethod::class.java)
    }

    override fun createUElementHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name != METHOD_NAME) return

                val containingClass = node.getParentOfType(UClass::class.java, false)?.javaPsi
                    ?: return
                if (!DIFF_UTIL_CLASSES.any {
                        context.evaluator.inheritsFrom(containingClass, it, false)
                    }) {
                    return
                }

                val body = node.uastBody ?: return
                body.accept(SuspiciousEqualityVisitor(context))
            }
        }
    }

    private class SuspiciousEqualityVisitor(
        private val context: JavaContext
    ) : AbstractUastVisitor() {

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            when (node.operator) {
                UastBinaryOperator.IDENTITY_EQUALS,
                UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
                    report(
                        node,
                        "Suspicious identity equality check in `areContentsTheSame`; use structural equality instead."
                    )
                }
                UastBinaryOperator.EQUALS,
                UastBinaryOperator.NOT_EQUALS -> {
                    if (isJava(context) && areReferenceTypes(node)) {
                        report(
                            node,
                            "Suspicious identity equality check in `areContentsTheSame`; use `equals()` instead."
                        )
                    }
                }
                else -> {}
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName == "equals" && node.valueArgumentCount == 1) {
                val receiverType = node.receiver?.getExpressionType()
                if (receiverType != null && !isEnumOrPrimitive(receiverType)) {
                    val method: PsiMethod? = node.resolve()
                    val containingClass = method?.containingClass
                    val qualifiedName = containingClass?.qualifiedName
                    if (qualifiedName == JAVA_OBJECT || qualifiedName == KOTLIN_ANY) {
                        report(
                            node,
                            "Suspicious `equals` call in `areContentsTheSame`; the receiver type does not override `equals`."
                        )
                    }
                }
            }
            return super.visitCallExpression(node)
        }

        private fun report(node: UElement, message: String) {
            context.report(ISSUE, node, context.getLocation(node), message)
        }

        private fun isJava(context: JavaContext): Boolean {
            return context.file.extension == "java"
        }

        private fun areReferenceTypes(node: UBinaryExpression): Boolean {
            return isReferenceType(node.leftOperand.getExpressionType()) &&
                    isReferenceType(node.rightOperand.getExpressionType())
        }

        private fun isReferenceType(type: PsiType?): Boolean {
            if (type == null) return true
            if (type is PsiPrimitiveType) return false
            val cls = context.evaluator.getTypeClass(type) ?: return true
            return !isEnumClass(cls)
        }

        private fun isEnumOrPrimitive(type: PsiType): Boolean {
            if (type is PsiPrimitiveType) return true
            val cls = context.evaluator.getTypeClass(type) ?: return false
            return isEnumClass(cls)
        }

        private fun isEnumClass(cls: PsiClass): Boolean {
            return cls.isEnum ||
                    cls.qualifiedName == "java.lang.Enum" ||
                    cls.qualifiedName == "kotlin.Enum"
        }
    }
}