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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.visitor.AbstractUastVisitor

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not also \
                call the super implementation, either because it is empty, or perhaps it \
                contains code not intended to be run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UastHandler {
        return UastHandler(context)
    }

    inner class UastHandler(private val context: JavaContext) : AbstractUastVisitor() {

        override fun visitMethod(node: UMethod): Boolean {
            // Check if this method overrides a super method annotated with @EmptySuper
            val superMethod = findSuperMethodWithEmptySuper(node) ?: return false

            // Visit the method body looking for super.method() calls
            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    checkSuperCall(node, superMethod)
                    return false
                }
            })

            return false
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
            // Handled inside visitMethod via nested visitor
            return false
        }

        private fun findSuperMethodWithEmptySuper(node: UMethod): PsiMethod? {
            val psiMethod = node.javaPsi
            val containingClass = psiMethod.containingClass ?: return null

            // Find super methods
            val superMethods = psiMethod.findSuperMethods()
            for (superMethod in superMethods) {
                if (context.evaluator.getAllAnnotations(superMethod, true)
                        .any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
                ) {
                    return superMethod
                }
            }
            return null
        }

        private fun checkSuperCall(call: UCallExpression, superMethod: PsiMethod) {
            // Check if this is a super.xxx() call
            val receiver = call.receiver
            if (receiver !is USuperExpression) return

            // Resolve the called method and check if it matches the super method
            val resolvedMethod = call.resolve() ?: return
            if (resolvedMethod == superMethod ||
                resolvedMethod.isEquivalentTo(superMethod) ||
                isSameMethod(resolvedMethod, superMethod)
            ) {
                val methodName = superMethod.name
                context.report(
                    ISSUE,
                    call,
                    context.getLocation(call),
                    "No need to call `super.${methodName}()`; the super method is defined to be empty"
                )
            }
        }

        private fun isSameMethod(method1: PsiMethod, method2: PsiMethod): Boolean {
            if (method1.name != method2.name) return false
            val class1 = method1.containingClass?.qualifiedName ?: return false
            val class2 = method2.containingClass?.qualifiedName ?: return false
            if (class1 != class2) return false
            val params1 = method1.parameterList.parameters
            val params2 = method2.parameterList.parameters
            if (params1.size != params2.size) return false
            for (i in params1.indices) {
                if (params1[i].type != params2[i].type) return false
            }
            return true
        }
    }
}