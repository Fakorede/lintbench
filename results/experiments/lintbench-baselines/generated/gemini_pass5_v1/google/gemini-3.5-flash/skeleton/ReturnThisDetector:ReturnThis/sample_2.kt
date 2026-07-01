package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ReturnThisDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = "Methods annotated with `@ReturnThis` (or overriding a method annotated with `@ReturnThis`) must return `this` to support chaining or builder patterns.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    private val checkedMethods = mutableSetOf<PsiMethod>()

    override fun applicableAnnotations(): List<String> {
        return listOf("ReturnThis")
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.DEFINITION
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotation: UAnnotation,
        qualifiedName: String,
    ) {
        val method = element as? UMethod ?: return
        checkMethod(context, method)
    }

    fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Intentionally left empty as validation is handled through visitClass and visitAnnotationUsage
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        for (method in declaration.methods) {
            val uMethod = method.toUElement() as? UMethod ?: continue
            if (isReturnThisAnnotated(context, uMethod)) {
                checkMethod(context, uMethod)
            }
        }
    }

    private fun isReturnThisAnnotated(context: JavaContext, method: UMethod): Boolean {
        if (hasReturnThis(context, method)) return true
        for (superMethod in method.findSuperMethods()) {
            if (hasReturnThis(context, superMethod)) return true
        }
        return false
    }

    private fun hasReturnThis(context: JavaContext, method: PsiMethod): Boolean {
        val annotations = context.evaluator.getAnnotations(method, false)
        for (annotation in annotations) {
            val fqn = annotation.qualifiedName
            if (fqn == "ReturnThis" || fqn?.endsWith(".ReturnThis") == true) {
                return true
            }
        }
        return false
    }

    private fun checkMethod(context: JavaContext, method: UMethod) {
        val psiMethod = method.javaPsi
        if (!checkedMethods.add(psiMethod)) return

        val body = method.uastBody ?: return
        val returns = mutableListOf<UReturnExpression>()
        collectReturnExpressions(body, returns)

        if (returns.isEmpty()) {
            val location = context.getNameLocation(method)
            context.report(
                ISSUE,
                method,
                location,
                "Method annotated with `@ReturnThis` must return `this`"
            )
        } else {
            for (ret in returns) {
                if (!isThisExpression(ret.returnExpression)) {
                    val location = context.getLocation(ret)
                    context.report(
                        ISSUE,
                        ret,
                        location,
                        "Method annotated with `@ReturnThis` must return `this`"
                    )
                }
            }
        }
    }

    private fun collectReturnExpressions(element: UElement, list: MutableList<UReturnExpression>) {
        if (element is UReturnExpression) {
            list.add(element)
        }
        if (element is UClass || element is ULambdaExpression) {
            return
        }
        for (child in element.uastChildren) {
            collectReturnExpressions(child, list)
        }
    }

    private fun isThisExpression(expression: UExpression?): Boolean {
        val unwrapped = unwrap(expression) ?: return false
        if (unwrapped is UThisExpression) return true
        if (unwrapped is UQualifiedReferenceExpression) {
            return unwrapped.selector is UThisExpression
        }
        return false
    }

    private fun unwrap(expression: UExpression?): UExpression? {
        var current = expression ?: return null
        while (true) {
            val skipped = current.skipParenthesizedExprDown()
            if (skipped != current) {
                current = skipped
                continue
            }
            if (current is UBinaryExpressionWithType) {
                current = current.operand
                continue
            }
            break
        }
        return current
    }
}