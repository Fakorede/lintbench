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
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            moreInfo = "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
        )

        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val CONTEXT_CLASS = "android.content.Context"
        private const val APP_COMPAT_DELEGATE_CLASS = "androidx.appcompat.app.AppCompatDelegate"
        private const val LOCALE_MANAGER_COMPAT_CLASS = "androidx.core.os.LocaleManagerCompat"
        private const val LOCALE_MANAGER_CLASS = "android.app.LocaleManager"

        private val LOCALE_METHODS = setOf(
            "setLocale",
            "setLocales",
            "updateConfiguration",
            "createConfigurationContext",
            "setApplicationLocales"
        )
    }

    override fun getApplicableMethodNames(): List<String> = LOCALE_METHODS.toList()

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        val methodName = method.name

        val isLocaleChange = when (methodName) {
            "setLocale", "setLocales" -> {
                evaluator.isMemberInClass(method, CONFIGURATION_CLASS)
            }
            "updateConfiguration" -> {
                evaluator.isMemberInSubClassOf(method, RESOURCES_CLASS, false)
            }
            "createConfigurationContext" -> {
                evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS, false)
            }
            "setApplicationLocales" -> {
                evaluator.isMemberInClass(method, APP_COMPAT_DELEGATE_CLASS) ||
                        evaluator.isMemberInClass(method, LOCALE_MANAGER_COMPAT_CLASS) ||
                        evaluator.isMemberInClass(method, LOCALE_MANAGER_CLASS)
            }
            else -> false
        }

        if (isLocaleChange) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = "Found a locale change at runtime; if this is an app bundle, " +
                        "locale-specific resources may not be available unless locale splits " +
                        "are disabled or the Play Core library is used to download additional " +
                        "language resources at runtime"
            )
        }
    }
}