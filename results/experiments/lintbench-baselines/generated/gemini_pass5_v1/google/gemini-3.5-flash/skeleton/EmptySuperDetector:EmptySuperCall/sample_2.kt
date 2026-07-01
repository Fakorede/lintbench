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
            explanation = """
                An overriding method should not call the super implementation of a method annotated \
                with `@EmptySuper`. This is either because the super implementation is empty, or \
                it contains code that is not intended to be run when the method is overridden.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java, UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // No action needed specifically for method declaration entry,
                // but we implement it to satisfy the starter skeleton structure.
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                val receiver = node.receiver
                if (receiver is USuperExpression) {
                    val method = node.resolve() ?: return
                    
                    val hasEmptySuper = context.evaluator.getAllAnnotations(method, false).any {
                        val qualifiedName = it.qualifiedName
                        qualifiedName == "androidx.annotation.EmptySuper" || 
                        qualifiedName?.endsWith(".EmptySuper") == true || 
                        qualifiedName == "EmptySuper"
                    }
                    
                    if (hasEmptySuper) {
                        var parent = node.uastParent
                        while (parent != null && parent !is UMethod) {
                            parent = parent.uastParent
                        }
                        val containingMethod = parent as? UMethod ?: return
                        val containingPsiMethod = containingMethod.javaPsi
                        
                        val overrides = containingPsiMethod.name == method.name && 
                            containingPsiMethod.findSuperMethods().any { superMethod ->
                                superMethod.isEquivalentTo(method) || superMethod == method
                            }
                        
                        if (overrides) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "No need to call `super.${method.name}` because it is annotated with `@EmptySuper`"
                            )
                        }
                    }
                }
            }
        }
}