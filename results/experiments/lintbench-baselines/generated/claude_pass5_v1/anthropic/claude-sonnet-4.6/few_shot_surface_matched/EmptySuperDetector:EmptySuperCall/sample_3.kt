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

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): EmptySuperVisitor =
        EmptySuperVisitor(context)

    inner class EmptySuperVisitor(private val context: JavaContext) : AbstractUastVisitor() {

        override fun visitMethod(node: UMethod): Boolean {
            // We only care about methods that override something
            if (!node.findSuperMethods().any()) return false

            node.uastBody?.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    checkSuperCall(node)
                    return false
                }
            })

            return false
        }

        private fun checkSuperCall(call: UCallExpression) {
            val receiver = call.receiver
            if (receiver !is USuperExpression) return

            val resolved = call.resolve() ?: return
            if (!isMemberAnnotatedWithEmptySuper(resolved)) return

            val methodName = resolved.name
            context.report(
                ISSUE,
                call,
                context.getLocation(call),
                "No need to call `super.$methodName`; the super method is defined to be empty"
            )
        }

        private fun isMemberAnnotatedWithEmptySuper(method: PsiMethod): Boolean {
            if (context.evaluator.hasAnnotation(method, EMPTY_SUPER_ANNOTATION)) {
                return true
            }
            // Also check super methods of the resolved method (annotation may be on a higher-level super)
            for (superMethod in method.findSuperMethods()) {
                if (context.evaluator.hasAnnotation(superMethod, EMPTY_SUPER_ANNOTATION)) {
                    return true
                }
            }
            return false
        }
    }
}