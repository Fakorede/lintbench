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
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = DiffUtilHandler(context)

    private class DiffUtilHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitMethod(node: UMethod) {
            if (node.name != "areContentsTheSame") return
            if (node.uastParameters.size != 2) return

            val containingClass = node.getParentOfType(UClass::class.java, true) ?: return
            if (!context.evaluator.extendsClass(
                    containingClass.javaPsi,
                    "androidx.recyclerview.widget.DiffUtil.ItemCallback",
                    false
                )
            ) {
                return
            }

            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                    checkBinary(node)
                    return super.visitBinaryExpression(node)
                }

                override fun visitCallExpression(node: UCallExpression): Boolean {
                    checkEqualsCall(node)
                    return super.visitCallExpression(node)
                }
            })
        }

        private fun checkBinary(node: UBinaryExpression) {
            val left = node.leftOperand.skipParenthesizedExprDown()
            val right = node.rightOperand.skipParenthesizedExprDown()

            if (isNullLiteral(left) || isNullLiteral(right)) return
            if (!isReferenceType(left) || !isReferenceType(right)) return

            when (node.operator) {
                UastBinaryOperator.IDENTITY_EQUALS,
                UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
                    report(
                        node,
                        "Using identity equality (=== or Java ==) in areContentsTheSame; " +
                                "use structural equality (==) to compare item contents"
                    )
                }

                UastBinaryOperator.EQUALS,
                UastBinaryOperator.NOT_EQUALS -> {
                    val leftClass = getTypeClass(left)
                    val rightClass = getTypeClass(right)
                    if ((leftClass != null && !hasCustomEquals(leftClass)) ||
                        (rightClass != null && !hasCustomEquals(rightClass))
                    ) {
                        report(
                            node,
                            "areContentsTheSame uses == on a type that does not override equals(); " +
                                    "this is equivalent to identity equality and may produce incorrect DiffUtil results"
                        )
                    }
                }

                else -> {}
            }
        }

        private fun checkEqualsCall(node: UCallExpression) {
            if (node.methodIdentifier?.name != "equals") return
            if (node.valueArgumentCount != 1) return

            val receiver = node.receiver ?: return
            val receiverClass = getTypeClass(receiver) ?: return
            if (!hasCustomEquals(receiverClass)) {
                report(
                    node,
                    "Calling equals() on a type that does not override equals(); " +
                            "this compares references and may produce incorrect DiffUtil results"
                )
            }
        }

        private fun isNullLiteral(expr: UExpression): Boolean {
            val literal = expr.skipParenthesizedExprDown()
            return literal is ULiteralExpression && literal.value == null
        }

        private fun isReferenceType(expr: UExpression): Boolean {
            val type = expr.getExpressionType() ?: return false
            return type !is PsiPrimitiveType && type.canonicalText != "void"
        }

        private fun getTypeClass(expr: UExpression): PsiClass? {
            val type = expr.getExpressionType() ?: return null
            val erased = TypeConversionUtil.erasure(type) ?: return null
            return PsiUtil.resolveClassInType(erased)
        }

        private fun hasCustomEquals(cls: PsiClass): Boolean {
            val fqName = cls.qualifiedName
            if (fqName == "java.lang.Object" || fqName == "kotlin.Any") return true
            if (cls.isInterface || cls.isEnum) return true
            if (cls.hasModifierProperty(PsiModifier.ABSTRACT)) return true

            val equalsMethod = context.evaluator.findInheritedMethod(cls, "equals", true, null)
                ?: return false
            val declaringClass = equalsMethod.containingClass ?: return false
            val declaringFqName = declaringClass.qualifiedName
            return declaringFqName != "java.lang.Object" && declaringFqName != "kotlin.Any"
        }

        private fun report(node: UElement, message: String) {
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is
                implemented incorrectly, such as using identity equals instead of equals, or
                calling equals on a class that has not implemented it, weird visual artifacts
                can occur.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DiffUtilDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}