package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.w3c.dom.Node

class EmptySuperDetector : Detector(), SourceCodeScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "EmptySuperCall",
            briefDescription = "Calling an empty super method",
            explanation = """
                For methods annotated with `@EmptySuper`, overriding methods should not also call \
                the super implementation, either because it is empty, or perhaps it contains \
                code not intended to be run when the method is overridden.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                EmptySuperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.receiver is USuperExpression) {
                    val method = node.resolve() ?: return
                    if (hasEmptySuperAnnotation(context, method)) {
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

    private fun hasEmptySuperAnnotation(context: JavaContext, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        val annotations = evaluator.getAnnotations(method, false)
        for (annotation in annotations) {
            val qualifiedName = annotation.qualifiedName
            if (qualifiedName == "androidx.annotation.EmptySuper" ||
                qualifiedName == "com.android.support.annotation.EmptySuper" ||
                qualifiedName?.endsWith(".EmptySuper") == true) {
                return true
            }
        }
        return false
    }
}