package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not also call \
                the super implementation, either because it is empty, or perhaps it contains \
                code not intended to be run when the method is overridden.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out org.jetbrains.uast.UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): AbstractUastVisitor {
        return object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.receiver is USuperExpression) {
                    val method = node.resolve() ?: return super.visitCallExpression(node)
                    if (hasEmptySuperAnnotation(context, method)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "No need to call `super.${method.name}` because it is annotated with `@EmptySuper`"
                        )
                    }
                }
                return super.visitCallExpression(node)
            }
        }
    }

    private fun hasEmptySuperAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        val annotations = evaluator.getAnnotations(method, false)
        for (annotation in annotations) {
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName == "androidx.annotation.EmptySuper" ||
                qualifiedName == "com.android.support.annotation.EmptySuper" ||
                qualifiedName?.endsWith(".EmptySuper") == true) {
                return true
            }
        }
        return false
    }
}