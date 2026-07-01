package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                Reference: https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private const val LOCALE_CLASS = "java.util.Locale"
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val CONTEXT_CLASS = "android.content.Context"
        private const val CONTEXT_WRAPPER_CLASS = "android.content.ContextWrapper"
        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val APP_COMPAT_DELEGATE_CLASS = "androidx.appcompat.app.AppCompatDelegate"
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "setDefault",
            "updateConfiguration",
            "createConfigurationContext",
            "applyOverrideConfiguration",
            "setApplicationLocales",
            "setLocale",
            "setLocales"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val evaluator = context.evaluator
        val containingClass = method.containingClass ?: return

        val shouldReport = when (methodName) {
            "setDefault" -> {
                evaluator.extendsClass(containingClass, LOCALE_CLASS, false)
            }
            "updateConfiguration" -> {
                evaluator.extendsClass(containingClass, RESOURCES_CLASS, false)
            }
            "createConfigurationContext" -> {
                evaluator.extendsClass(containingClass, CONTEXT_CLASS, false)
            }
            "applyOverrideConfiguration" -> {
                evaluator.extendsClass(containingClass, CONTEXT_WRAPPER_CLASS, false)
            }
            "setApplicationLocales" -> {
                val qualifiedName = containingClass.qualifiedName ?: ""
                qualifiedName == APP_COMPAT_DELEGATE_CLASS
            }
            "setLocale", "setLocales" -> {
                evaluator.extendsClass(containingClass, CONFIGURATION_CLASS, false)
            }
            else -> false
        }

        if (shouldReport) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Found a locale change at runtime. Ensure the app bundle is configured " +
                        "to not split by locale, or use the Play Core library to download " +
                        "additional locales at runtime. See " +
                        "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
            )
        }
    }
}