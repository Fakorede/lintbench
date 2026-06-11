package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    override fun getApplicableConstructorTypes(): List<String>? {
        return listOf("com.google.android.play.core.splitcompat.SplitCompat")
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        if (constructor.containingClass?.qualifiedName == "com.google.android.play.core.splitcompat.SplitCompat") {
            // If SplitCompat is used, it's assumed that the app handles locale changes properly.
            return
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() as? PsiMethod ?: return
                if (method.containingClass?.qualifiedName == "com.android.bundle.Config") {
                    checkAppBundleConfig(context, node)
                }
            }
        }
    }

    private fun checkAppBundleConfig(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName == "setLocaleSplittingEnabled" && node.valueArguments.size >= 1) {
            val argumentValue = node.valueArguments[0].evaluate() as? Boolean ?: return
            if (!argumentValue) {
                // Locale splitting is disabled, which is good for runtime locale changes.
                return
            }
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Ensure that the app bundle is configured to handle runtime locale changes properly."
        )
    }

    companion object {
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), the Android App Bundle must be configured to not split by locale or the Play Core library must be used to download additional locales at runtime.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}