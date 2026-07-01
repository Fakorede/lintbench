package com.android.tools.lint.checks

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

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
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

        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val APP_COMPAT_DELEGATE_CLASS = "androidx.appcompat.app.AppCompatDelegate"
        private const val LOCALE_MANAGER_COMPAT_CLASS = "androidx.core.app.LocaleManagerCompat"
        private const val LOCALE_MANAGER_CLASS = "android.app.LocaleManager"
        private const val CONTEXT_CLASS = "android.content.Context"

        private val CONFIGURATION_LOCALE_SETTERS = setOf("setLocale", "setLocales")
        private val CONTEXT_UPDATE_METHODS = setOf("createConfigurationContext", "applyOverrideConfiguration")
        private val RESOURCES_UPDATE_METHODS = setOf("updateConfiguration")
        private val APP_COMPAT_LOCALE_METHODS = setOf("setApplicationLocales")
        private val LOCALE_MANAGER_METHODS = setOf("setApplicationLocales")
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
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: ""
        val evaluator = context.evaluator

        val isLocaleChange = when {
            methodName in CONFIGURATION_LOCALE_SETTERS &&
                    (qualifiedName == CONFIGURATION_CLASS ||
                            evaluator.extendsClass(containingClass, CONFIGURATION_CLASS, false)) -> true

            methodName in CONTEXT_UPDATE_METHODS &&
                    (qualifiedName == CONTEXT_CLASS ||
                            evaluator.extendsClass(containingClass, CONTEXT_CLASS, false)) -> true

            methodName in RESOURCES_UPDATE_METHODS &&
                    (qualifiedName == RESOURCES_CLASS ||
                            evaluator.extendsClass(containingClass, RESOURCES_CLASS, false)) -> true

            methodName in APP_COMPAT_LOCALE_METHODS &&
                    (qualifiedName == APP_COMPAT_DELEGATE_CLASS ||
                            evaluator.extendsClass(containingClass, APP_COMPAT_DELEGATE_CLASS, false)) -> true

            methodName in LOCALE_MANAGER_METHODS &&
                    (qualifiedName == LOCALE_MANAGER_COMPAT_CLASS ||
                            qualifiedName == LOCALE_MANAGER_CLASS ||
                            evaluator.extendsClass(containingClass, LOCALE_MANAGER_COMPAT_CLASS, false) ||
                            evaluator.extendsClass(containingClass, LOCALE_MANAGER_CLASS, false)) -> true

            else -> false
        }

        if (isLocaleChange) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Found a locale change using `$methodName`; if using App Bundles, the base " +
                        "module must be configured to not split by locale, or the Play Core " +
                        "library must be used to download additional locales at runtime."
            )
        }
    }
}