package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.USuperExpression

class EmptySuperDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver !is USuperExpression) return
                val resolved = node.resolve() as? PsiModifierListOwner ?: return
                if (hasEmptySuperAnnotation(resolved)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Do not call super for methods annotated with @EmptySuper"
                    )
                }
            }
        }
    }

    private fun hasEmptySuperAnnotation(owner: PsiModifierListOwner): Boolean {
        return owner.modifierList?.annotations?.any { annotation ->
            val name = annotation.qualifiedName
            name == "EmptySuper" || name?.endsWith(".EmptySuper") == true
        } == true
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with `@EmptySuper` indicate that the super implementation is empty \
                or should not be called when overridden. Calling `super` in such methods is unnecessary \
                or incorrect.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}