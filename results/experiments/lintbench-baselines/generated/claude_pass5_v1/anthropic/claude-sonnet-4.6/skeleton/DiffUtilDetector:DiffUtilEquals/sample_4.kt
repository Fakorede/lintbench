package com.android.tools.lint.checks

import com.android.tools.lint.client.api.TYPE_OBJECT
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
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DiffUtilEquals",
            briefDescription = "Suspicious DiffUtil Equality",
            explanation = """
                `areContentsTheSame` is used by `DiffUtil` to produce diffs. If the \
                method is implemented incorrectly, such as using identity equals \
                instead of equals, or calling equals on a class that has not implemented \
                it, weird visual artifacts can occur.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val DIFF_UTIL_CALLBACK_OLD = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        DIFF_UTIL_CALLBACK,
        DIFF_UTIL_CALLBACK_OLD,
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val method = declaration.methods.firstOrNull { it.name == ARE_CONTENTS_THE_SAME } ?: return
        method.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                checkBinaryExpression(context, node)
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                checkCallExpression(context, node)
                return super.visitCallExpression(node)
            }
        })
    }

    override fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        checkBinaryExpression(context, node)
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        checkCallExpression(context, node)
    }

    private fun checkBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
            node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            val leftType = node.leftOperand.getExpressionType()
            if (leftType != null && !leftType.equalsToText("boolean") &&
                !leftType.equalsToText("int") &&
                !leftType.equalsToText("long") &&
                !leftType.equalsToText("double") &&
                !leftType.equalsToText("float") &&
                !leftType.equalsToText("byte") &&
                !leftType.equalsToText("char") &&
                !leftType.equalsToText("short")
            ) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Suspicious identity equality (`===`) instead of `equals()`"
                )
            }
        }
    }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName != "equals") return

        val method: PsiMethod = node.resolve() ?: return

        val containingClass: PsiClass = method.containingClass ?: return

        // Check if equals is defined on Object (not overridden)
        if (containingClass.qualifiedName == TYPE_OBJECT ||
            containingClass.qualifiedName == "java.lang.Object"
        ) {
            // equals is inherited from Object - not overridden
            val receiverType: PsiType? = node.receiverType
            if (receiverType != null) {
                val receiverClass = resolveClass(context, receiverType)
                if (receiverClass != null && !hasOverriddenEquals(receiverClass)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "equals() is not implemented in `${receiverClass.name}` - this will use identity equality"
                    )
                }
            }
        }
    }

    private fun resolveClass(context: JavaContext, type: PsiType): PsiClass? {
        return com.intellij.psi.PsiTypesUtil.getPsiClass(type)
    }

    private fun hasOverriddenEquals(psiClass: PsiClass): Boolean {
        // Check if the class itself declares equals (not inherited from Object)
        for (method in psiClass.methods) {
            if (method.name == "equals") {
                val params = method.parameterList.parameters
                if (params.size == 1 && (params[0].type.equalsToText(TYPE_OBJECT) ||
                            params[0].type.equalsToText("java.lang.Object"))
                ) {
                    return true
                }
            }
        }
        // Also check superclasses (but not Object itself)
        val superClass = psiClass.superClass ?: return false
        val superName = superClass.qualifiedName ?: return false
        if (superName == "java.lang.Object") return false
        return hasOverriddenEquals(superClass)
    }
}