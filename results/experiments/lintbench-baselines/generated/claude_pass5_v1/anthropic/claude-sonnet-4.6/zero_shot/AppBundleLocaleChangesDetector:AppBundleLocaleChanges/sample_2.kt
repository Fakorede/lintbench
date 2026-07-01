/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

/**
 * Detector that warns when an app changes locale at runtime while using App Bundles
 * without properly handling locale splits.
 *
 * When using Android App Bundles, if you change the locale at runtime (e.g. for an
 * in-app language switcher), you must either:
 * 1. Configure the bundle to not split by locale, or
 * 2. Use Play Core library to download additional locales at runtime.
 */
class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    /**
     * Tracks whether the project uses App Bundle locale splits (default is true when using bundles).
     * Set to false when we detect that language splits are explicitly disabled or Play Core
     * SplitInstallManager is used.
     */
    private var localeChangeCalls = mutableListOf<Pair<Location, String>>()
    private var hasLanguageSplitsDisabled = false
    private var hasSplitInstallManager = false

    companion object {
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val CONTEXT_CLASS = "android.content.Context"
        private const val LOCALE_CLASS = "java.util.Locale"
        private const val LOCALE_LIST_CLASS = "android.os.LocaleList"

        private const val UPDATE_CONFIGURATION_METHOD = "updateConfiguration"
        private const val CREATE_CONFIGURATION_CONTEXT_METHOD = "createConfigurationContext"
        private const val SET_DEFAULT_METHOD = "setDefault"
        private const val SET_LOCALES_METHOD = "setLocales"
        private const val SET_LOCALE_METHOD = "setLocale"

        // Play Core SplitInstallManager
        private const val SPLIT_INSTALL_MANAGER_CLASS =
            "com.google.android.play.core.splitinstall.SplitInstallManager"
        private const val SPLIT_INSTALL_MANAGER_FACTORY_CLASS =
            "com.google.android.play.core.splitinstall.SplitInstallManagerFactory"

        // AppCompatDelegate locale methods
        private const val APP_COMPAT_DELEGATE_CLASS =
            "androidx.appcompat.app.AppCompatDelegate"
        private const val SET_APPLICATION_LOCALES_METHOD = "setApplicationLocales"

        val ISSUE = Issue.create(
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
            moreInfo = "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes",
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
                EnumSet.of(Scope.JAVA_FILE),
                EnumSet.of(Scope.GRADLE_FILE)
            )
        )

        private val LOCALE_CHANGE_METHODS = mapOf(
            RESOURCES_CLASS to setOf(UPDATE_CONFIGURATION_METHOD),
            CONFIGURATION_CLASS to setOf(SET_LOCALE_METHOD, SET_LOCALES_METHOD),
            CONTEXT_CLASS to setOf(CREATE_CONFIGURATION_CONTEXT_METHOD),
            LOCALE_CLASS to setOf(SET_DEFAULT_METHOD),
            LOCALE_LIST_CLASS to setOf(SET_DEFAULT_METHOD)
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            UPDATE_CONFIGURATION_METHOD,
            CREATE_CONFIGURATION_CONTEXT_METHOD,
            SET_DEFAULT_METHOD,
            SET_LOCALES_METHOD,
            SET_LOCALE_METHOD,
            SET_APPLICATION_LOCALES_METHOD
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: return

        // Check if AppCompatDelegate.setApplicationLocales() is used - this is the recommended
        // way to handle in-app language changes and is safe with App Bundles
        if (containingClass == APP_COMPAT_DELEGATE_CLASS &&
            methodName == SET_APPLICATION_LOCALES_METHOD
        ) {
            // AppCompatDelegate.setApplicationLocales handles locale changes properly
            // but still worth flagging if using app bundles without proper configuration
            val location = context.getLocation(node)
            localeChangeCalls.add(Pair(location, methodName))
            return
        }

        // Check for Play Core SplitInstallManager usage
        if (containingClass == SPLIT_INSTALL_MANAGER_CLASS ||
            containingClass == SPLIT_INSTALL_MANAGER_FACTORY_CLASS
        ) {
            hasSplitInstallManager = true
            return
        }

        // Check for locale-changing method calls
        val isLocaleChange = LOCALE_CHANGE_METHODS.any { (className, methods) ->
            methods.contains(methodName) &&
                context.evaluator.extendsClass(
                    context.evaluator.findClass(containingClass),
                    className,
                    true
                )
        }

        if (isLocaleChange) {
            val location = context.getLocation(node)
            localeChangeCalls.add(Pair(location, methodName))
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        valueCookie: Any,
        statementCookie: Any
    ) {
        // Check for language splits configuration in build.gradle
        // bundle { language { enableSplit = false } }
        if (property == "enableSplit" && parent == "language") {
            val boolValue = value.trim()
            if (boolValue == "false") {
                hasLanguageSplitsDisabled = true
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (hasSplitInstallManager || hasLanguageSplitsDisabled) {
            // App is properly handling locale changes with App Bundles
            localeChangeCalls.clear()
            return
        }

        for ((location, methodName) in localeChangeCalls) {
            context.report(
                ISSUE,
                location,
                "Found a locale change using `$methodName` which may not work correctly " +
                    "with App Bundles if language splits are enabled. Either disable language " +
                    "splits in your bundle configuration or use the Play Core library to " +
                    "download language splits at runtime.",
            )
        }

        localeChangeCalls.clear()
    }

    override fun afterCheckRootProject(context: Context) {
        // Reset state after checking the root project
        hasLanguageSplitsDisabled = false
        hasSplitInstallManager = false
        localeChangeCalls.clear()
    }
}