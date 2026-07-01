package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

class DiffUtilDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "androidx.recyclerview.widget.DiffUtil.Callback",
            "androidx.recyclerview.widget.DiffUtil.ItemCallback",
            "android.support.v7.util.DiffUtil.Callback",
            "android.support.v7.util.DiffUtil.ItemCallback"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.name == "areContentsTheSame") {
                method.accept(DiffUtilVisitor(context))
            }
        }
    }

    private class DiffUtilVisitor(private val context: JavaContext) : AbstractUastVisitor() {

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            val operator = node.operator
            if (operator == UastBinaryOperator.EQUALS ||
                operator == UastBinaryOperator.NOT_EQUALS ||
                operator == UastBinaryOperator.IDENTITY_EQUALS ||
                operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
            ) {
                if (isNullLiteral(node.leftOperand) || isNullLiteral(node.rightOperand)) {
                    return super.visitBinaryExpression(node)
                }

                val leftType = node.leftOperand.getExpressionType()

                if (operator == UastBinaryOperator.IDENTITY_EQUALS ||
                    operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Use of identity equality (`===` or `!==`) instead of content equality (`==` or `!=` / `equals` in Java) in `areContentsTheSame`"
                    )
                } else {
                    checkType(leftType, node)
                }
            }
            return super.visitBinaryExpression(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val methodName = node.methodName
            if (methodName == "equals") {
                val receiver = node.receiver
                if (receiver != null && !isNullLiteral(receiver)) {
                    checkType(receiver.getExpressionType(), node)
                }
            } else if (methodName == "equals" && node.valueArgumentCount == 2) {
                val resolved = node.resolve()
                if (resolved != null && context.evaluator.isMemberInClass(resolved, "java.util.Objects")) {
                    val firstArg = node.valueArguments.getOrNull(0)
                    if (firstArg != null && !isNullLiteral(firstArg)) {
                        checkType(firstArg.getExpressionType(), node)
                    }
                }
            } else if (methodName == "areEqual" && node.valueArgumentCount == 2) {
                val resolved = node.resolve()
                if (resolved != null && context.evaluator.isMemberInClass(resolved, "kotlin.jvm.internal.Intrinsics")) {
                    val firstArg = node.valueArguments.getOrNull(0)
                    if (firstArg != null && !isNullLiteral(firstArg)) {
                        checkType(firstArg.getExpressionType(), node)
                    }
                }
            }
            return super.visitCallExpression(node)
        }

        private fun isNullLiteral(element: UElement): Boolean {
            return element is ULiteralExpression && element.value == null
        }

        private fun checkType(type: PsiType?, node: UElement) {
            if (type == null) return
            if (type is PsiArrayType) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Comparing arrays using `equals` or `==` works by identity. Use `Arrays.equals` or `contentEquals` instead"
                )
                return
            }
            if (type is PsiClassType) {
                val psiClass = type.resolve() ?: return
                if (!overridesEquals(psiClass)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Comparing class `${psiClass.name}` which does not override `equals`"
                    )
                }
            }
        }

        private fun overridesEquals(psiClass: PsiClass): Boolean {
            if (psiClass.isInterface || psiClass.isEnum || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return true
            }
            if (psiClass is KtLightClass) {
                val origin = psiClass.kotlinOrigin
                if (origin is KtClass) {
                    if (origin.isData() || origin.isSealed()) {
                        return true
                    }
                } else if (origin is KtObjectDeclaration) {
                    return true
                }
            }
            if (psiClass.superClass?.qualifiedName == "java.lang.Record") {
                return true
            }
            var current: PsiClass? = psiClass
            while (current != null) {
                val qName = current.qualifiedName
                if (qName == "java.lang.Object") {
                    break
                }
                for (method in current.findMethodsByName("equals", false)) {
                    val parameters = method.parameterList.parameters
                    if (parameters.size == 1) {
                        val paramType = parameters[0].type
                        if (paramType.canonicalText == "java.lang.Object") {
                            return true
                        }
                    }
                }
                current = current.superClass
            }
            return false
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the method is \
                implemented incorrectly, such as using identity equals instead of equals, or \
                calling equals on a class that has not implemented it, weird visual artifacts \
                can occur.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}