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
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
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
            priority = 4,
            severity = Severity.ERROR,
            moreInfo = "https://issuetracker.google.com/116789824",
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val DIFF_UTIL_CLASS = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val DIFF_UTIL_CLASS_OLD = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(DIFF_UTIL_CLASS, DIFF_UTIL_CLASS_OLD)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            if (method.name == ARE_CONTENTS_THE_SAME) {
                checkMethod(context, method)
            }
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        method.accept(object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
                val operator = node.operator
                if (operator == UastBinaryOperator.IDENTITY_EQUALS ||
                    operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
                ) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Suspicious equality check: did you mean `.equals()` instead of `===`?"
                    )
                }
                return super.visitBinaryExpression(node)
            }

            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "equals") {
                    val receiverType = node.receiver?.getExpressionType()
                    if (receiverType != null) {
                        checkTypeForEquals(context, node, receiverType)
                    }
                }
                return super.visitCallExpression(node)
            }
        })
    }

    private fun checkTypeForEquals(context: JavaContext, node: UCallExpression, type: PsiType) {
        if (type !is PsiClassType) return
        val psiClass = type.resolve() ?: return
        if (!hasOverriddenEquals(psiClass)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: `${psiClass.name}` does not override `equals()`"
            )
        }
    }

    private fun hasOverriddenEquals(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName
        // Object/Any itself doesn't count as having a meaningful equals
        if (qualifiedName == "java.lang.Object" || qualifiedName == "kotlin.Any") {
            return false
        }

        // Check methods declared directly in this class
        for (method in psiClass.methods) {
            if (method.name == "equals") {
                val params = method.parameterList.parameters
                if (params.size == 1) {
                    val paramType = params[0].type.canonicalText
                    if (paramType == "java.lang.Object" || paramType == "Object") {
                        return true
                    }
                }
            }
        }

        // Check superclass recursively (but stop at Object)
        val superClass = psiClass.superClass
        if (superClass != null) {
            val superName = superClass.qualifiedName
            if (superName != null && superName != "java.lang.Object" && superName != "kotlin.Any") {
                if (hasOverriddenEquals(superClass)) {
                    return true
                }
            }
        }

        // Check interfaces (e.g., sealed class hierarchies where interface defines equals)
        for (iface in psiClass.interfaces) {
            if (hasOverriddenEquals(iface)) {
                return true
            }
        }

        return false
    }
}