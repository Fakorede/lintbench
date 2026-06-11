package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), the Android App Bundle must be configured to not split by locale or the Play Core library must be used to download additional locales at runtime.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("updateConfiguration", "applyOverrideConfiguration")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Potential runtime locale change without proper App Bundle configuration"
            )
        }
    }

    override fun getApplicableConstructorTypes(): List<String>? = null
    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {}
    override fun getApplicableReferenceNames(): List<String>? = null
    override fun visitClass(context: JavaContext, declaration: UClass) {}
    override fun visitClass(context: JavaContext, lambda: ULambdaExpression) {}
    override fun appliesToResourceRefs() = false
    override fun applicableAnnotations(): List<String>? = null
    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = false
    override fun inheritAnnotation(annotation: String): Boolean = false
    override fun isCallGraphRequired(): Boolean = false

    // Ensure the detector initializes correctly
    init {
        Companion.ISSUE
    }
}