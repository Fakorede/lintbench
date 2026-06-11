package com.android.tools.lint.checks

import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ISSUE_ID = "DeprecatedCipherProvider"
        private const val ISSUE_NAME = "Using BC Provider"
        private const val ISSUE_EXPLANATION =
            "The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher."
        private const val BC_PROVIDER = "BC"

        @VisibleForTesting
        internal val ISSUE: Issue = Issue.create(
            id = ISSUE_ID,
            briefDescription = ISSUE_NAME,
            explanation = ISSUE_EXPLANATION,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstance")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiver = node.receiver as? UQualifiedReferenceExpression ?: return

        if (receiver.selectorName != "getProvider") return
        val providerArg = node.valueArguments.firstOrNull() ?: return
        val providerValue = providerArg.asRenderString()

        if (providerValue == BC_PROVIDER) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using deprecated `BC` provider"
            )
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

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return null
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return null
    }

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