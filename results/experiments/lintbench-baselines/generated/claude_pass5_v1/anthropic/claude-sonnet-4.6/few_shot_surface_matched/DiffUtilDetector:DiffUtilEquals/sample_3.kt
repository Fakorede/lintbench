package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

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
            priority = 4,
            severity = Severity.ERROR,
            implementation = Implementation(
                DiffUtilDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true,
            moreInfo = "https://issuetracker.google.com/116789824"
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.Callback"
        private const val DIFF_UTIL_ITEM_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val SUPPORT_DIFF_UTIL_CALLBACK = "android.support.v7.util.DiffUtil.Callback"
        private const val SUPPORT_DIFF_UTIL_ITEM_CALLBACK = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        DIFF_UTIL_CALLBACK,
        DIFF_UTIL_ITEM_CALLBACK,
        SUPPORT_DIFF_UTIL_CALLBACK,
        SUPPORT_DIFF_UTIL_ITEM_CALLBACK
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val methods = declaration.methods
        for (method in methods) {
            if (method.name == ARE_CONTENTS_THE_SAME) {
                checkMethod(context, method)
            }
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
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
        // This is called for binary expressions at the top level; actual checking
        // is done within the class visitor above via the inner visitor.
    }

    override fun visitCallExpression(context: JavaContext, node: UCallExpression) {
        // This is called for call expressions at the top level; actual checking
        // is done within the class visitor above via the inner visitor.
    }

    private fun checkBinaryExpression(context: JavaContext, node: UBinaryExpression) {
        val operator = node.operator
        if (operator == UastBinaryOperator.IDENTITY_EQUALS || operator == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
            val leftType = node.leftOperand.getExpressionType() ?: return
            val rightType = node.rightOperand.getExpressionType() ?: return

            // Primitive types are fine with identity comparison
            if (leftType.canonicalText == "boolean" ||
                leftType.canonicalText == "int" ||
                leftType.canonicalText == "long" ||
                leftType.canonicalText == "float" ||
                leftType.canonicalText == "double" ||
                leftType.canonicalText == "byte" ||
                leftType.canonicalText == "char" ||
                leftType.canonicalText == "short"
            ) {
                return
            }
            if (rightType.canonicalText == "boolean" ||
                rightType.canonicalText == "int" ||
                rightType.canonicalText == "long" ||
                rightType.canonicalText == "float" ||
                rightType.canonicalText == "double" ||
                rightType.canonicalText == "byte" ||
                rightType.canonicalText == "char" ||
                rightType.canonicalText == "short"
            ) {
                return
            }

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: Did you mean `.equals()` instead of `===`?"
            )
        }
    }

    private fun checkCallExpression(context: JavaContext, node: UCallExpression) {
        if (node.methodName != "equals") return

        val method = node.resolve() ?: return

        val containingClass = method.containingClass ?: return

        // Check if the class has overridden equals — if it's declared in Object, it's not overridden
        val qualifiedName = containingClass.qualifiedName ?: return
        if (qualifiedName == "java.lang.Object") {
            val receiverType = node.receiver?.getExpressionType()
            val typeName = receiverType?.canonicalText ?: qualifiedName
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: `equals()` is not implemented in `$typeName`"
            )
            return
        }

        // Check if equals is inherited from Object without override
        if (!hasOverriddenEquals(method, containingClass)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Suspicious equality check: `equals()` is not overridden in `${containingClass.name}`"
            )
        }
    }

    private fun hasOverriddenEquals(method: PsiMethod, containingClass: com.intellij.psi.PsiClass): Boolean {
        // If the method's containing class is Object, it's not overridden
        if (containingClass.qualifiedName == "java.lang.Object") return false

        // Check if the class itself declares equals (not just inherits it)
        val declaredMethods = containingClass.methods
        for (declaredMethod in declaredMethods) {
            if (declaredMethod.name == "equals" &&
                declaredMethod.parameterList.parametersCount == 1
            ) {
                val paramType = declaredMethod.parameterList.parameters[0].type.canonicalText
                if (paramType == "java.lang.Object") {
                    return true
                }
            }
        }

        // Check superclasses (but not Object itself)
        val superClass = containingClass.superClass ?: return false
        if (superClass.qualifiedName == "java.lang.Object") return false

        return hasOverriddenEquals(method, superClass)
    }
}