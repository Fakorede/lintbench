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
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UastCallKind

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            EmptySuperDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                Methods annotated with `@EmptySuper` indicate that overriding implementations should not invoke
                the super implementation. The base method may be empty or may contain code that should not run
                when the method is overridden. Remove the `super.xxx()` call.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // No method-level analysis needed; super calls are handled in visitCallExpression.
            }

            override fun visitCallExpression(node: UCallExpression) {
                if (node.kind != UastCallKind.METHOD_CALL) return
                if (node.receiver !is USuperExpression) return

                val resolved = node.resolve() ?: return
                if (!resolved.hasEmptySuperAnnotation()) return

                val methodName = node.methodName ?: return
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Overriding implementations should not call `super.$methodName()`: the base method is annotated `@EmptySuper`"
                )
            }
        }

    private fun PsiMethod.hasEmptySuperAnnotation(): Boolean {
        val modifierList = modifierList ?: return false
        return modifierList.annotations.any { annotation ->
            val name = annotation.qualifiedName
            name == "EmptySuper" || name?.substringAfterLast('.') == "EmptySuper"
        }
    }
}