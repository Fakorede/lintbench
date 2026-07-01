package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.util.isMethodCall

/**
 * Detector that warns when an app changes locale at runtime without
 * properly configuring App Bundle locale splitting or using Play Core.
 */
class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                If the app is distributed as an App Bundle and changes the locale at runtime \
                without either disabling locale splits or using the Play Core library to \
                download the required locale resources, the app may crash or display incorrect \
                resources because the locale-specific resources may not be available on device.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
        )

        // Configuration.setLocale / setLocales
        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"

        // Locale setter method names on Configuration
        private val CONFIGURATION_LOCALE_METHODS = setOf(
            "setLocale",
            "setLocales"
        )

        // Resources.updateConfiguration (deprecated but still used)
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val UPDATE_CONFIGURATION_METHOD = "updateConfiguration"

        // Context.createConfigurationContext
        private const val CONTEXT_CLASS = "android.content.Context"
        private const val CREATE_CONFIGURATION_CONTEXT = "createConfigurationContext"

        // AppCompatDelegate.setApplicationLocales (AndroidX)
        private const val APP_COMPAT_DELEGATE_CLASS =
            "androidx.appcompat.app.AppCompatDelegate"
        private const val SET_APPLICATION_LOCALES = "setApplicationLocales"

        // LocaleManagerCompat / LocaleManager
        private const val LOCALE_MANAGER_COMPAT_CLASS =
            "androidx.core.os.LocaleManagerCompat"
        private const val LOCALE_MANAGER_CLASS = "android.app.LocaleManager"
        private const val SET_APPLICATION_LOCALES_MANAGER = "setApplicationLocales"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setLocale",
        "setLocales",
        UPDATE_CONFIGURATION_METHOD,
        CREATE_CONFIGURATION_CONTEXT,
        SET_APPLICATION_LOCALES,
        SET_APPLICATION_LOCALES_MANAGER
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        val methodName = method.name

        val isLocaleChange = when {
            methodName in CONFIGURATION_LOCALE_METHODS &&
                    containingClass == CONFIGURATION_CLASS -> true

            methodName == UPDATE_CONFIGURATION_METHOD &&
                    context.evaluator.extendsClass(
                        context.evaluator.findClass(containingClass),
                        RESOURCES_CLASS,
                        true
                    ) -> true

            methodName == CREATE_CONFIGURATION_CONTEXT &&
                    context.evaluator.extendsClass(
                        context.evaluator.findClass(containingClass),
                        CONTEXT_CLASS,
                        true
                    ) -> true

            methodName == SET_APPLICATION_LOCALES &&
                    (containingClass == APP_COMPAT_DELEGATE_CLASS ||
                            containingClass == LOCALE_MANAGER_COMPAT_CLASS) -> true

            methodName == SET_APPLICATION_LOCALES_MANAGER &&
                    containingClass == LOCALE_MANAGER_CLASS -> true

            else -> false
        }

        if (isLocaleChange) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "Found a locale change at runtime. If the app is distributed " +
                        "as an App Bundle, locale-specific resources may not be available " +
                        "unless locale splits are disabled or the Play Core library is used " +
                        "to download additional language resources at runtime. " +
                        "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
            )
        }
    }
}