package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsage
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UsageInfo
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getParentOfType

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANNOTATION = "ReturnThis"

        @JvmField
        val RETURN_THIS = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis`, or methods that override a method
                annotated with `@ReturnThis`, must return `this`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    private val returnThisMethods = mutableMapOf<UClass, MutableSet<UMethod>>()

    override fun beforeCheckFile(context: Context) {
        returnThisMethods.clear()
    }

    override fun applicableAnnotations() = listOf(ANNOTATION)

    override fun isApplicableAnnotationUsage(type: AnnotationUsage) = true

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: UsageInfo?
    ) {
        val method = element as? UMethod ?: return
        val cls = method.getParentOfType(UClass::class.java, true) ?: return
        returnThisMethods.getOrPut(cls) { mutableSetOf() }.add(method)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val set = returnThisMethods.getOrPut(declaration) { mutableSetOf() }
        for (method in declaration.methods) {
            if (requiresReturnThis(context, method)) {
                set.add(method)
            }
        }
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        val method = node.getParentOfType(UMethod::class.java, true) ?: return
        val cls = method.getParentOfType(UClass::class.java, true) ?: return
        val set = returnThisMethods[cls] ?: return
        if (method !in set) return
        if (method.returnType == PsiType.VOID) return

        if (returnsThis(node.returnExpression)) return

        val location = context.getLocation(node)
        val message = "Method must return `this` because it is annotated with @ReturnThis (or overrides a method that is)"
        context.report(Incident(RETURN_THIS, node, location, message))
    }

    private fun requiresReturnThis(context: JavaContext, method: UMethod): Boolean {
        val psi = (method.javaPsi as? PsiMethod) ?: (method.psi as? PsiMethod) ?: return false
        if (psi.hasReturnThisAnnotation()) return true

        var superMethod = context.evaluator.findSuperMethod(psi)
        while (superMethod != null) {
            if (superMethod.hasReturnThisAnnotation()) return true
            superMethod = context.evaluator.findSuperMethod(superMethod)
        }
        return false
    }

    private fun PsiMethod.hasReturnThisAnnotation(): Boolean {
        return annotations.any {
            val name = it.qualifiedName
            name == ANNOTATION || name?.endsWith(".$ANNOTATION") == true
        }
    }

    private fun returnsThis(expression: UExpression?): Boolean {
        var expr = expression
        while (true) {
            expr = when {
                expr is UParenthesizedExpression -> expr.expression
                expr is UCastExpression -> expr.operand
                else -> break
            }
        }
        return expr is UThisExpression
    }
}