package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UClass
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UElement

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {}

    override fun getApplicableConstructorTypes(): List<String>? = null

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {}

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {}

    override fun applicableSuperClasses(): List<String>? = null

    override fun visitClass(context: JavaContext, declaration: UClass) {}

    override fun visitClass(context: JavaContext, lambda: ULambdaExpression) {}

    override fun appliesToResourceRefs(): Boolean = true

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
        if (type == ResourceType.DIMEN && name.startsWith("inset_")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using internal inset dimension resource. Use `androidx.core.view.WindowInsetsCompat` and related APIs instead."
            )
        }
    }

    override fun applicableAnnotations(): List<String>? = null

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
        reference: org.w3c.dom.Node,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {}

    override fun isCallGraphRequired(): Boolean = false

    companion object {
        private val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                Using internal inset dimension resources is not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI.
                
                To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}