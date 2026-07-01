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
            androidSpecific = true,
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
            // We only care about methods that override something
            if (!node.findSuperMethods().any { isSuperMethodAnnotatedWithEmptySuper(it) }) {
                return false
            }

            // Visit the body of this method looking for super calls
            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    visitCallExpression(node)
                    return false
                }
            })

            node.uastBody?.accept(SuperCallVisitor(context, node))
            return false
        }

        private fun isSuperMethodAnnotatedWithEmptySuper(method: PsiMethod): Boolean {
            return context.evaluator.getAllAnnotations(method, inHierarchy = false)
                .any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
        }

        private fun UMethod.findSuperMethods(): List<PsiMethod> {
            val psiMethod = this.javaPsi
            return context.evaluator.getSuperMethod(psiMethod)?.let { listOf(it) } ?: emptyList()
        }
    }

    private inner class SuperCallVisitor(
        private val context: JavaContext,
        private val containingMethod: UMethod
    ) : AbstractUastVisitor() {

        override fun visitCallExpression(node: UCallExpression): Boolean {
            checkSuperCall(node)
            return false
        }

        private fun checkSuperCall(node: UCallExpression) {
            val receiver = node.receiver
            if (receiver !is USuperExpression) {
                return
            }

            val resolvedMethod = node.resolve() ?: return

            // Check if the resolved method matches the overridden method annotated with @EmptySuper
            val superMethods = getSuperMethods(containingMethod)
            for (superMethod in superMethods) {
                if (resolvedMethod == superMethod && isAnnotatedWithEmptySuper(superMethod)) {
                    val methodName = node.methodName ?: resolvedMethod.name
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "No need to call `super.${methodName}()`; the super method is defined to be empty"
                    )
                    break
                }
            }
        }

        private fun getSuperMethods(method: UMethod): List<PsiMethod> {
            val psiMethod = method.javaPsi
            val superMethod = context.evaluator.getSuperMethod(psiMethod)
            return if (superMethod != null) listOf(superMethod) else emptyList()
        }

        private fun isAnnotatedWithEmptySuper(method: PsiMethod): Boolean {
            return context.evaluator.getAllAnnotations(method, inHierarchy = false)
                .any { it.qualifiedName == EMPTY_SUPER_ANNOTATION }
        }
    }

    // Top-level UAST visitor that dispatches to UastHandler
    override fun getApplicableMethodNames(): List<String>? = null

    override fun applicableSuperClasses(): List<String>? = null
}