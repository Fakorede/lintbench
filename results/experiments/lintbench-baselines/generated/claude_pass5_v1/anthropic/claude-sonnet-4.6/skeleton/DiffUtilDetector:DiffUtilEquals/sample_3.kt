package com.android.tools.lint.checks

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

    override fun visitBinaryExpression(
        context: JavaContext,
        node: UBinaryExpression,
    ) {
        checkBinaryExpression(context, node)
    }

    override fun visitCallExpression(
        context: JavaContext,
        node: UCallExpression,
    ) {
        checkCallExpression(context, node)
    }

    private fun checkBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        if (node.operator != UastBinaryOperator.IDENTITY_EQUALS &&
            node.operator != UastBinaryOperator.IDENTITY_NOT_EQUALS
        ) {
            return
        }

        val enclosingMethod = node.getEnclosingMethod() ?: return
        if (!isAreContentsTheSame(enclosingMethod)) return

        val leftType = node.leftOperand.getExpressionType() ?: return
        if (isPrimitive(leftType)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Suspicious equality check: Did you mean `.equals()` instead of `==`?",
        )
    }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        if (node.methodName != "equals") return

        val enclosingMethod = node.getEnclosingMethod() ?: return
        if (!isAreContentsTheSame(enclosingMethod)) return

        val psiMethod: PsiMethod = node.resolve() ?: return
        val containingClass: PsiClass = psiMethod.containingClass ?: return

        if (!definesEquals(containingClass)) {
            val qualifiedName = containingClass.qualifiedName ?: containingClass.name ?: "unknown"
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: `$qualifiedName` does not implement `equals()`",
            )
        }
    }

    private fun isAreContentsTheSame(method: UMethod): Boolean {
        return method.name == ARE_CONTENTS_THE_SAME
    }

    private fun isPrimitive(type: PsiType): Boolean {
        return type is com.intellij.psi.PsiPrimitiveType
    }

    private fun definesEquals(psiClass: PsiClass): Boolean {
        // Check if the class itself (not inherited from Object) defines equals
        val methods = psiClass.findMethodsByName("equals", false)
        if (methods.isNotEmpty()) {
            for (method in methods) {
                val params = method.parameterList.parameters
                if (params.size == 1) {
                    val paramType = params[0].type
                    if (paramType.equalsToText("java.lang.Object") || paramType.equalsToText("Object")) {
                        return true
                    }
                }
            }
        }
        // Check superclasses (but not Object itself)
        val superClass = psiClass.superClass ?: return false
        val superName = superClass.qualifiedName ?: return false
        if (superName == "java.lang.Object" || superName == "kotlin.Any") {
            return false
        }
        return definesEquals(superClass)
    }

    private fun UBinaryExpression.getEnclosingMethod(): UMethod? {
        var parent = this.uastParent
        while (parent != null) {
            if (parent is UMethod) return parent
            parent = parent.uastParent
        }
        return null
    }

    private fun UCallExpression.getEnclosingMethod(): UMethod? {
        var parent = this.uastParent
        while (parent != null) {
            if (parent is UMethod) return parent
            parent = parent.uastParent
        }
        return null
    }
}