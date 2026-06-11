package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ISSUE_ID = "DeprecatedSinceApi"
        private const val BRIEF_DESCRIPTION = "Using a method deprecated in earlier SDK"
        private const val EXPLANATION =
            """
            Some backport methods are only necessary until a specific version of Android. These have been annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions.
            Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.
            """

        private val ISSUE = Issue.create(
            id = ISSUE_ID,
            briefDescription = BRIEF_DESCRIPTION,
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return null
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val deprecatedAnnotation = method.annotations.find { it.qualifiedName == "DeprecatedSinceApi" }
        if (deprecatedAnnotation != null) {
            val apiLevelAttr = deprecatedAnnotation.attributes.firstOrNull { it.name == "apiLevel" }?.value
            val minSdkVersion = context.getModule()?.getProjectData()?.minSdkVersion ?: 0

            if (apiLevelAttr is Int && minSdkVersion >= apiLevelAttr) {
                val replacementAttr = deprecatedAnnotation.attributes.find { it.name == "replacement" }
                val message = buildString {
                    append("Call to deprecated method ")
                    append(method.name)
                    append(". This method is only necessary for API levels below ${apiLevelAttr}.")
                    if (replacementAttr != null && replacementAttr.value is String) {
                        append(" Use ")
                        append(replacementAttr.value as String)
                        append(" instead.")
                    }
                }

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    message
                )
            }
        }
    }

    override fun getApplicableConstructorTypes(): List<String>? = null

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {}

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {}

    override fun applicableSuperClasses(): List<String>? = null

    override fun visitClass(context: JavaContext, declaration: UClass) {}

    override fun visitClass(context: JavaContext, lambda: ULambdaExpression) {}

    override fun appliesToResourceRefs() = false

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
    }

    override fun applicableAnnotations(): List<String>? = null

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {}

    override fun getApplicableUastTypes() = null

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethodCallExpression(node: UCallExpression) {
            val context = node.uastParent?.let { context.with(node, it) } ?: return
            super.visitMethodCallExpression(node)
            visitMethodCall(context, node, node.resolveMethod())
        }
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = false

    override fun inheritAnnotation(annotation: String): Boolean = false

    override fun visitAnnotationUsage(
        context: XmlContext,
        reference: Node,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {}

    override fun isCallGraphRequired() = false

    override fun analyzeCallGraph(context: Context, callGraph: CallGraphResult) {}
}