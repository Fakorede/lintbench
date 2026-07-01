package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
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
import org.jetbrains.uast.getParentOfType

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not also \
                call the super implementation, either because it is empty, or perhaps it \
                contains code not intended to be run when the method is overridden.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java, UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            private var currentMethod: UMethod? = null

            override fun visitMethod(node: UMethod) {
                currentMethod = node
            }

            override fun visitCallExpression(node: UCallExpression) {
                val receiver = node.receiver
                val isSuper = receiver is USuperExpression || receiver?.asSourceString() == "super"
                if (isSuper) {
                    val method = node.resolve() ?: return
                    if (hasEmptySuperAnnotation(context, method)) {
                        val curr = currentMethod ?: node.getParentOfType(UMethod::class.java) ?: return
                        if (curr.name == method.name) {
                            val message = "No need to call `super.${method.name}` because it is annotated with `@EmptySuper`"
                            context.report(
                                Incident(ISSUE, node, context.getLocation(node), message)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun hasEmptySuperAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        if (context.evaluator.hasAnnotation(method, "androidx.annotation.EmptySuper", true)) {
            return true
        }
        for (annotation in method.annotations) {
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName != null) {
                if (qualifiedName == "EmptySuper" || qualifiedName.endsWith(".EmptySuper")) {
                    return true
                }
            } else {
                val nameReferenceElement = annotation.nameReferenceElement
                if (nameReferenceElement != null && nameReferenceElement.referenceName == "EmptySuper") {
                    return true
                }
            }
        }
        return false
    }
}