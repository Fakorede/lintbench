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
            explanation = "Methods annotated with `@EmptySuper` are empty, or their super implementations " +
                    "are not intended to be run when the method is overridden. Calling these super methods " +
                    "is redundant and should be avoided.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java, UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // No-op
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver is USuperExpression) {
                    val targetMethod = node.resolve() ?: return
                    if (targetMethod.isConstructor) return

                    val annotations = context.evaluator.getAnnotations(targetMethod, inHierarchy = false)
                    val hasEmptySuper = annotations.any {
                        val name = it.qualifiedName
                        name == "androidx.annotation.EmptySuper" || name == "EmptySuper" || name?.endsWith(".EmptySuper") == true
                    }
                    if (hasEmptySuper) {
                        val methodName = targetMethod.name
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "No need to call `super.$methodName`; the super method is empty"
                        )
                    }
                }
            }
        }
}