package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANNOTATION_NAME = "EmptySuper"

        private val IMPLEMENTATION = Implementation(
            EmptySuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with `@EmptySuper` are empty (or contain code that should not be
                run when the method is overridden). Overriding implementations should therefore
                not call `super` for these methods.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java, UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Super calls are handled in visitCallExpression.
            }

            override fun visitCallExpression(node: UCallExpression) {
                if (!node.isSuperCall()) return

                val target = (node as? UResolvable)?.resolve() as? PsiMethod ?: return
                if (!target.hasEmptySuperAnnotation()) return

                val enclosingMethod = findEnclosingMethod(node) ?: return
                if (!enclosingMethod.isOverrideOf(target, context)) return

                val message = "Calling `super.${target.name}` is not recommended; " +
                        "the super method is annotated `@EmptySuper`."
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message,
                )
            }
        }

    private fun UCallExpression.isSuperCall(): Boolean {
        if (receiver is USuperExpression) return true
        val text = sourcePsi?.text?.trimStart() ?: return false
        return text.startsWith("super.")
    }

    private fun PsiMethod.hasEmptySuperAnnotation(): Boolean {
        val modifierList = modifierList ?: return false
        for (annotation in modifierList.annotations) {
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName != null && qualifiedName.endsWith(".$ANNOTATION_NAME")) return true

            val referenceName = annotation.nameReferenceElement?.referenceName
            if (referenceName == ANNOTATION_NAME) return true
        }
        return false
    }

    private fun findEnclosingMethod(call: UCallExpression): UMethod? {
        var parent: UElement? = call.uastParent
        while (parent != null && parent !is UMethod) {
            parent = parent.uastParent
        }
        return parent as? UMethod
    }

    private fun UMethod.isOverrideOf(superMethod: PsiMethod, context: JavaContext): Boolean {
        if (name != superMethod.name) return false

        val methodPsi = javaPsi as? PsiMethod ?: return false
        if (methodPsi.parameterList.parametersCount != superMethod.parameterList.parametersCount) {
            return false
        }

        val superClass = superMethod.containingClass ?: return false
        val containingClass = methodPsi.containingClass ?: return false
        return context.evaluator.extendsClass(
            containingClass,
            superClass.qualifiedName,
            false,
        )
    }
}