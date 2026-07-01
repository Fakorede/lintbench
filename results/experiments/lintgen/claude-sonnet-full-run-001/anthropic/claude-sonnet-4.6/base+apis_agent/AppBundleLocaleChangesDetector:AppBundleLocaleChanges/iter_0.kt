package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.util.isMethodCall

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
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
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
        )

        // Configuration class methods
        private val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private val LOCALE_CLASS = "java.util.Locale"

        // Methods that set locale on a Configuration object
        private val CONFIGURATION_LOCALE_SETTERS = setOf(
            "setLocale",
            "setLocales"
        )

        // Context methods that update configuration
        private val CONTEXT_UPDATE_METHODS = setOf(
            "createConfigurationContext",
            "applyOverrideConfiguration"
        )

        // Resources methods
        private val RESOURCES_UPDATE_METHODS = setOf(
            "updateConfiguration"
        )

        // AppCompatDelegate locale methods
        private val APP_COMPAT_DELEGATE_CLASS = "androidx.appcompat.app.AppCompatDelegate"
        private val APP_COMPAT_LOCALE_METHODS = setOf(
            "setApplicationLocales",
            "setDefaultNightMode"
        )

        // LocaleManagerCompat / LocaleManager
        private val LOCALE_MANAGER_COMPAT_CLASS = "androidx.core.app.LocaleManagerCompat"
        private val LOCALE_MANAGER_CLASS = "android.app.LocaleManager"
        private val LOCALE_MANAGER_METHODS = setOf(
            "setApplicationLocales"
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return (CONFIGURATION_LOCALE_SETTERS +
                CONTEXT_UPDATE_METHODS +
                RESOURCES_UPDATE_METHODS +
                APP_COMPAT_LOCALE_METHODS +
                LOCALE_MANAGER_METHODS).toList()
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: return

        val isLocaleChange = when {
            methodName in CONFIGURATION_LOCALE_SETTERS &&
                    containingClass == CONFIGURATION_CLASS -> true

            methodName in CONTEXT_UPDATE_METHODS &&
                    isContextSubclass(context, method) -> {
                // Only flag if the configuration being passed has locale changes
                // We flag createConfigurationContext and applyOverrideConfiguration
                // as they are commonly used for locale switching
                true
            }

            methodName in RESOURCES_UPDATE_METHODS &&
                    isResourcesClass(context, method) -> true

            methodName in APP_COMPAT_LOCALE_METHODS &&
                    containingClass == APP_COMPAT_DELEGATE_CLASS -> true

            methodName in LOCALE_MANAGER_METHODS &&
                    (containingClass == LOCALE_MANAGER_COMPAT_CLASS ||
                            containingClass == LOCALE_MANAGER_CLASS) -> true

            else -> false
        }

        if (isLocaleChange) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Found a locale change using `${methodName}`. If using App Bundles, " +
                        "the base module must be configured to not split by locale, or the " +
                        "Play Core library must be used to download additional locales at runtime. " +
                        "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
            )
        }
    }

    private fun isContextSubclass(context: JavaContext, method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        val evaluator = context.evaluator
        return evaluator.extendsClass(containingClass, "android.content.Context", true) ||
                evaluator.extendsClass(containingClass, "android.content.ContextWrapper", true) ||
                containingClass.qualifiedName == "android.content.Context" ||
                containingClass.qualifiedName == "android.content.ContextWrapper"
    }

    private fun isResourcesClass(context: JavaContext, method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false
        return containingClass.qualifiedName == "android.content.res.Resources" ||
                context.evaluator.extendsClass(
                    containingClass,
                    "android.content.res.Resources",
                    true
                )
    }
}