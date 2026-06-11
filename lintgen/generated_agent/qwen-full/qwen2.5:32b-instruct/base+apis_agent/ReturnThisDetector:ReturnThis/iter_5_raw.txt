package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*
import java.util.*

class ReturnThisDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ReturnThis",
            briefDescription = "Method must return `this`",
            explanation = """
                Methods annotated with `@ReturnThis` should also return `this`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ReturnThisDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            )
        )

        private val RETURN_THIS_ANNOTATION = "Landroidx/annotation/ReturnThis;"
    }

    override fun getApplicableMethodNames(): List<String>? {
        return null
    }

    override fun visitMethod(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // Not needed for this detector
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return null
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        // Not needed for this detector
    }

    override fun getApplicableReferenceNames(): List<String>? {
        return null
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        // Not needed for this detector
    }

    override fun applicableSuperClasses(): List<String>? {
        return null
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Not needed for this detector
    }

    override fun visitClass(context: JavaContext, lambda: ULambdaExpression) {
        // Not needed for this detector
    }

    override fun appliesToResourceRefs(): Boolean {
        return false
    }

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
        // Not needed for this detector
    }

    override fun applicableAnnotations(): List<String>? {
        return listOf(RETURN_THIS_ANNOTATION)
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        if (element is UMethod) {
            val returnType = element.returnType ?: return

            // Check if the method returns `this`
            if (!returnType.equals(element.containingClass?.uastType)) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Methods annotated with @ReturnThis should also return `this`"
                )
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return null
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return null
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD
    }

    override fun inheritAnnotation(annotation: String): Boolean {
        return false
    }
}