package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class DiffUtilDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DiffUtilDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

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
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://issuetracker.google.com/116789824"
        )

        private const val DIFF_UTIL_CALLBACK = "androidx.recyclerview.widget.DiffUtil.ItemCallback"
        private const val DIFF_UTIL_CALLBACK_OLD = "android.support.v7.util.DiffUtil.ItemCallback"
        private const val ARE_CONTENTS_THE_SAME = "areContentsTheSame"
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(DIFF_UTIL_CALLBACK, DIFF_UTIL_CALLBACK_OLD)
    }

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
                if (node.operator == UastBinaryOperator.IDENTITY_EQUALS ||
                    node.operator == UastBinaryOperator.IDENTITY_NOT_EQUALS
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
                    val receiver = node.receiver
                    if (receiver != null) {
                        val receiverType = receiver.getExpressionType()
                        if (receiverType != null) {
                            val psiClass = context.evaluator.getTypeClass(receiverType)
                            if (psiClass != null && !hasCustomEquals(context, psiClass)) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Suspicious equality check: `${psiClass.name}` does not " +
                                            "override `equals()`, so this is the same as identity equality"
                                )
                            }
                        }
                    }
                }
                return super.visitCallExpression(node)
            }
        })
    }

    private fun hasCustomEquals(context: JavaContext, psiClass: PsiClass): Boolean {
        // Check if the class itself (not inherited from Object) overrides equals
        val qualifiedName = psiClass.qualifiedName
        // java.lang.Object has equals but it's identity-based; we want to know if
        // the class or any of its superclasses (other than Object) has overridden equals
        if (qualifiedName == "java.lang.Object") {
            return false
        }

        // Check if the class directly declares equals
        val methods = psiClass.findMethodsByName("equals", false)
        for (method in methods) {
            val params = method.parameterList.parameters
            if (params.size == 1) {
                val paramType = params[0].type.canonicalText
                if (paramType == "java.lang.Object" || paramType == "Object") {
                    return true
                }
            }
        }

        // Check superclasses recursively (but not Object)
        val superClass = psiClass.superClass ?: return false
        val superQualifiedName = superClass.qualifiedName
        if (superQualifiedName == "java.lang.Object") {
            return false
        }

        return hasCustomEquals(context, superClass)
    }
}