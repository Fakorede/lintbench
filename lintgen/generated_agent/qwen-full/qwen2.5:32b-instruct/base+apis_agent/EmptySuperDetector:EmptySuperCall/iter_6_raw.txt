package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class EmptySuperDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ISSUE_ID = "EmptySuperCall"
        private const val ISSUE_NAME = "Calling an empty super method"
        private const val ISSUE_EXPLANATION =
            "Overriding methods should not call the super implementation if it is annotated with `@EmptySuper`."
        private const val ANNOTATION_NAME = "Landroidx/annotation/EmptySuper;"

        val ISSUE: Issue = Issue.create(
            id = ISSUE_ID,
            briefDescription = ISSUE_NAME,
            explanation = ISSUE_EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return null
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val superMethods = context.evaluator.getSuperMethods(method) ?: return

        for (superMethod in superMethods) {
            if (context.evaluator.isAnnotated(superMethod, ANNOTATION_NAME)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Do not call the super implementation of a method annotated with `@EmptySuper`."
                )
                return
            }
        }
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return null
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {}

    override fun getApplicableReferenceNames(): List<String>? {
        return null
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {}

    override fun applicableSuperClasses(): List<String>? {
        return null
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {}

    override fun visitClass(context: JavaContext, lambda: ULambdaExpression) {}

    override fun appliesToResourceRefs(): Boolean = false

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
    }

    override fun applicableAnnotations(): List<String>? {
        return null
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {}

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    override fun createUastHandler(context: JavaContext): UElementHandler? = null

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = false

    override fun inheritAnnotation(annotation: String): Boolean = false

    override fun visitAnnotationUsage(
        context: XmlContext,
        reference: Node,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {}

    override fun isCallGraphRequired(): Boolean = false

    override fun analyzeCallGraph(context: Context, callGraph: CallGraphResult) {}
}