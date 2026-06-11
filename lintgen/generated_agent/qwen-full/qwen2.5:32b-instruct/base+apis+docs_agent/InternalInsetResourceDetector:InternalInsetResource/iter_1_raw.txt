package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? {
        return null
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {}

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

    override fun appliesToResourceRefs(): Boolean = true

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
        if (type == ResourceType.DIMEN && name.startsWith("internal_inset")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using internal inset dimension resource is not supported. Use WindowInsetsCompat and related APIs instead."
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

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return null
    }

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
            severity = Severity.WARNING
        )
    }
}