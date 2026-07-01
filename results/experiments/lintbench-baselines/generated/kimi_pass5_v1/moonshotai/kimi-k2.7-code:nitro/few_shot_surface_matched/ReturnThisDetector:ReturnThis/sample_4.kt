package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getParentOfType

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val RETURN_THIS_MESSAGE =
            "Method annotated with @ReturnThis (or overriding one) must return `this`"

        @JvmField
        val RETURN_THIS =
            Issue.create(
                id = "ReturnThis",
                briefDescription = "Method must return `this`",
                explanation =
                    """
                        Methods annotated with `@ReturnThis`, or overriding a method annotated with
                        `@ReturnThis`, must return `this`.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation = Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)
            )
    }

    private val requiredMethods = mutableMapOf<UClass, MutableSet<UMethod>>()

    override fun beforeCheckFile(context: JavaContext) {
        requiredMethods.clear()
    }

    override fun applicableAnnotations(): List<String> = listOf("ReturnThis")

    override fun isApplicableAnnotationUsage(
        type: AnnotationUsageType,
        annotation: UAnnotation,
        caller: UElement?,
        member: PsiElement?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ): Boolean {
        return type == AnnotationUsageType.METHOD_OVERRIDE ||
                type == AnnotationUsageType.DECLARATION
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>,
        isImplicit: Boolean
    ): Boolean {
        val uMethod = usage as? UMethod ?: return false
        val uClass = uMethod.getParentOfType(UClass::class.java, true) ?: return false

        val set = requiredMethods.getOrPut(uClass) { mutableSetOf() }
        if (!set.add(uMethod)) {
            return true
        }

        // Catch single-expression function bodies (implicit return).
        val body = uMethod.uastBody
        if (body != null && body !is UBlockExpression && body !is UThisExpression) {
            context.report(
                Incident(
                    RETURN_THIS,
                    uMethod,
                    context.getNameLocation(uMethod),
                    RETURN_THIS_MESSAGE
                )
            )
        }

        return true
    }

    override fun visitReturnExpression(context: JavaContext, node: UReturnExpression) {
        // Make sure this return belongs to the method, not a nested lambda.
        var parent: UElement? = node.uastParent
        while (parent != null && parent !is UMethod) {
            if (parent is ULambdaExpression) {
                return
            }
            parent = parent.uastParent
        }

        val method = parent as? UMethod ?: return
        val uClass = method.getParentOfType(UClass::class.java, true) ?: return
        val set = requiredMethods[uClass] ?: return
        if (!set.contains(method)) return

        val returned = node.returnExpression
        if (returned is UThisExpression) {
            return
        }

        context.report(
            Incident(
                RETURN_THIS,
                node,
                context.getLocation(node),
                RETURN_THIS_MESSAGE
            )
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        requiredMethods.getOrPut(declaration) { mutableSetOf() }
    }
}