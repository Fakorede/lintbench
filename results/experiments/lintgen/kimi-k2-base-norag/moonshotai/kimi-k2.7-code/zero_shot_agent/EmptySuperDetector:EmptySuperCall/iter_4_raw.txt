package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.util.UastUtils

class EmptySuperDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.METHOD_CALL) {
                    return
                }

                val receiver = node.receiver ?: return
                if (receiver !is USuperExpression) {
                    return
                }

                val calledMethod = node.resolve() as? PsiMethod ?: return
                if (!calledMethod.hasAnnotation(ANNOTATION)) {
                    return
                }

                val containingMethod =
                    UastUtils.getParentOfType(node, UMethod::class.java, true) ?: return
                val containingClass =
                    UastUtils.getParentOfType(containingMethod, UClass::class.java, true) ?: return

                if (!context.evaluator.isMemberInSubClassOf(calledMethod, containingClass, false)) {
                    return
                }

                context.report(
                    ISSUE,
                    node,
                    context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                    MESSAGE
                )
            }
        }

    private fun PsiModifierListOwner.hasAnnotation(simpleName: String): Boolean {
        val list = modifierList ?: return false
        return list.annotations.any { annotation ->
            val qualified = annotation.qualifiedName
            qualified == simpleName || qualified?.substringAfterLast('.') == simpleName
        }
    }

    companion object {
        private const val ANNOTATION = "EmptySuper"
        private const val MESSAGE = "Calling an empty super method"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with @EmptySuper should not be invoked via super
                from overriding methods, because the super implementation is empty
                or contains code that should not run when the method is overridden.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}