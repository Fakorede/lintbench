/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import java.util.EnumSet

/**
 * Detector that warns when an app changes its locale at runtime without properly
 * configuring the Android App Bundle to handle language splits.
 *
 * When distributing via App Bundle, the base APK may not include all locale resources.
 * If the app switches locales at runtime (e.g. in-app language switcher), it must either:
 *  1. Disable locale splitting in the bundle config, or
 *  2. Use the Play Core library to download the required language resources on demand.
 */
class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {

        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val LOCALE_CLASS = "java.util.Locale"
        private const val LOCALE_LIST_CLASS = "android.os.LocaleList"

        // AppCompat / AndroidX locale helper
        private const val APP_COMPAT_DELEGATE_CLASS =
            "androidx.appcompat.app.AppCompatDelegate"

        // Android 13+ per-app language preference
        private const val LOCALE_MANAGER_CLASS = "android.app.LocaleManager"

        // Configuration fields that indicate locale changes
        private const val LOCALE_FIELD = "locale"
        private const val LOCALES_FIELD = "locales"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation =
                """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                If your app is distributed as an Android App Bundle and you change the locale \
                at runtime, you must either:
                1. Disable locale splitting in your bundle configuration by adding \
                `android.bundle.enableUncompressedNativeLibs=false` or configuring \
                `splits { language { enable false } }` in your build.gradle, or
                2. Use the Play Core library's `SplitInstallManager` to request the required \
                language splits before switching locales.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes \
                for more information.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE),
                EnumSet.of(Scope.JAVA_FILE)
            ),
            moreInfo =
                "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
        )

        private const val MESSAGE =
            "Found a locale change at runtime. If this app is distributed using Android " +
                "App Bundles, the bundle must be configured to not split by locale or the " +
                "Play Core library must be used to download additional locales at runtime. " +
                "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        // AppCompatDelegate.setApplicationLocales() / setDefaultNightMode() with locale
        "setApplicationLocales",
        // LocaleManager.setApplicationLocales() (Android 13+)
        "setApplicationLocales",
        // Configuration.setLocale() / setLocales()
        "setLocale",
        "setLocales",
        // Locale.setDefault()
        "setDefault",
        // Resources.updateConfiguration() - deprecated but still used
        "updateConfiguration",
        // Context.createConfigurationContext()
        "createConfigurationContext"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        val methodName = method.name

        val shouldReport = when {
            // AppCompatDelegate.setApplicationLocales(LocaleListCompat)
            containingClass == APP_COMPAT_DELEGATE_CLASS &&
                methodName == "setApplicationLocales" -> true

            // LocaleManager.setApplicationLocales(LocaleList) - Android 13+
            containingClass == LOCALE_MANAGER_CLASS &&
                methodName == "setApplicationLocales" -> true

            // Configuration.setLocale(Locale) or Configuration.setLocales(LocaleList)
            containingClass == CONFIGURATION_CLASS &&
                (methodName == "setLocale" || methodName == "setLocales") -> true

            // Locale.setDefault(Locale) or Locale.setDefault(Locale.Category, Locale)
            containingClass == LOCALE_CLASS &&
                methodName == "setDefault" -> true

            // Resources.updateConfiguration(Configuration, DisplayMetrics) - deprecated
            // Check if any argument is a Configuration that may have locale changes
            methodName == "updateConfiguration" &&
                isResourcesUpdateConfigurationCall(context, node, method) -> true

            // Context.createConfigurationContext(Configuration)
            methodName == "createConfigurationContext" &&
                isContextMethod(context, method) -> true

            else -> false
        }

        if (shouldReport) {
            context.report(
                issue = ISSUE,
                scope = node,
                location = context.getLocation(node),
                message = MESSAGE
            )
        }
    }

    /**
     * Checks whether the given method call is a call to Resources.updateConfiguration().
     */
    private fun isResourcesUpdateConfigurationCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ): Boolean {
        val containingClass = method.containingClass?.qualifiedName ?: return false
        // android.content.res.Resources or subclasses
        return containingClass == "android.content.res.Resources" ||
            isSubclassOf(context, containingClass, "android.content.res.Resources")
    }

    /**
     * Checks whether the given method belongs to a Context or subclass.
     */
    private fun isContextMethod(
        context: JavaContext,
        method: PsiMethod
    ): Boolean {
        val containingClass = method.containingClass?.qualifiedName ?: return false
        return containingClass == "android.content.Context" ||
            containingClass == "android.content.ContextWrapper" ||
            isSubclassOf(context, containingClass, "android.content.Context")
    }

    /**
     * Simple helper to check class hierarchy using the evaluator.
     */
    private fun isSubclassOf(
        context: JavaContext,
        className: String,
        superClassName: String
    ): Boolean {
        val evaluator = context.evaluator
        val cls = evaluator.findClass(className) ?: return false
        return evaluator.extendsClass(cls, superClassName, false)
    }
}