package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("setLanguage", "updateConfiguration")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
            val issue = Issue.create(
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

            context.report(issue, node, context.getLocation(node), "Potential runtime locale change without proper App Bundle configuration")
        }
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        return null
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {}

    override fun getApplicableReferenceNames(): List<String>? {
        return null
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {}

    override fun visitClass(context: JavaContext, lambda: ULambdaExpression) {}

    override fun appliesToResourceRefs(): Boolean {
        return false
    }

    override fun applicableAnnotations(): List<String>? {
        return null
    }

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return false
    }

    override fun inheritAnnotation(annotation: String): Boolean {
        return false
    }

    override fun isCallGraphRequired(): Boolean {
        return false
    }
}