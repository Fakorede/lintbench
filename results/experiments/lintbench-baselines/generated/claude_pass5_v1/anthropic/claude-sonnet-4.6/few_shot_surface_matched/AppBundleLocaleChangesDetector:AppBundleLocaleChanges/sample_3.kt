package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        private const val KEY_LOCALE_CHANGE_LOCATION = "localeChangeLocation"
        private const val KEY_SPLITS_LOCALE_DISABLED = "splitsLocaleDisabled"
        private const val KEY_PLAY_CORE_USED = "playCoreUsed"

        private const val LOCALE_CLASS = "java.util.Locale"
        private const val LOCALE_LIST_CLASS = "android.os.LocaleList"
        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"

        // Play Core SplitInstallManager method
        private const val SPLIT_INSTALL_REQUEST_BUILDER = "SplitInstallRequestBuilder"

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale, or the Play Core \
                library must be used to download additional locales at runtime.

                If the app bundle splits by locale (the default) and the app changes the locale \
                at runtime without using Play Core to download the required locale resources, \
                the app will likely crash with a missing resource exception.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            ),
            androidSpecific = true,
        )

        // Methods that indicate a runtime locale change
        private val LOCALE_CHANGE_METHODS = listOf(
            "setLocale",
            "setLocales",
            "setDefault",
            "applyOverrideConfiguration",
            "updateConfiguration",
            "updateLocale",
        )

        // Reference names that indicate locale manipulation
        private val LOCALE_REFERENCE_NAMES = listOf(
            "locale",
            "locales",
        )
    }

    override fun getApplicableMethodNames(): List<String> = LOCALE_CHANGE_METHODS

    override fun getApplicableReferenceNames(): List<String> = LOCALE_REFERENCE_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: return

        val isLocaleChange = when (methodName) {
            "setLocale" -> {
                containingClass == CONFIGURATION_CLASS ||
                    containingClass == "android.app.LocaleManager"
            }
            "setLocales" -> {
                containingClass == CONFIGURATION_CLASS ||
                    containingClass == "android.app.LocaleManager"
            }
            "setDefault" -> {
                containingClass == LOCALE_CLASS
            }
            "applyOverrideConfiguration" -> {
                context.evaluator.extendsClass(
                    context.evaluator.findClass(containingClass),
                    "android.content.ContextWrapper",
                    false
                )
            }
            "updateConfiguration" -> {
                containingClass == "android.content.res.Resources"
            }
            "updateLocale" -> {
                true
            }
            else -> false
        }

        if (isLocaleChange) {
            val location = context.getLocation(node)
            reportOrStoreLocaleChange(context, location)
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement,
    ) {
        // Check if this is setting a locale-related field on a Configuration object
        val referenceName = reference.resolvedName ?: return
        if (referenceName != "locale" && referenceName != "locales") return

        if (referenced !is com.intellij.psi.PsiField) return
        val containingClass = referenced.containingClass?.qualifiedName ?: return

        if (containingClass == CONFIGURATION_CLASS) {
            val location = context.getLocation(reference)
            reportOrStoreLocaleChange(context, location)
        }
    }

    private fun reportOrStoreLocaleChange(context: JavaContext, location: Location) {
        if (context.isGlobalAnalysis()) {
            // In global analysis mode, check directly
            context.getPartialResults(ISSUE).map().apply {
                if (!containsKey(KEY_LOCALE_CHANGE_LOCATION)) {
                    put(KEY_LOCALE_CHANGE_LOCATION, location)
                }
            }
        } else {
            context.getPartialResults(ISSUE).map().apply {
                if (!containsKey(KEY_LOCALE_CHANGE_LOCATION)) {
                    put(KEY_LOCALE_CHANGE_LOCATION, location)
                }
            }
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any,
    ) {
        // Check for splits { language { enable false } } or language { enable = false }
        if (property == "enable" && value == "false") {
            if (parent == "language" &&
                (parentParent == "splits" || parentParent == null)
            ) {
                // Locale splitting is disabled — this is the safe configuration
                context.getPartialResults(ISSUE).map().put(KEY_SPLITS_LOCALE_DISABLED, true)
            }
        }

        // Check for dependency on Play Core (SplitInstall)
        if (property == "implementation" || property == "api" ||
            property == "compile" || property == "runtimeOnly"
        ) {
            if (value.contains("play:core") ||
                value.contains("play.core") ||
                value.contains("splitinstall") ||
                value.contains("split-install") ||
                value.contains("com.google.android.play")
            ) {
                context.getPartialResults(ISSUE).map().put(KEY_PLAY_CORE_USED, true)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // Nothing extra needed here; results are collected via partial results
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var localeChangeLocation: Location? = null
        var splitsLocaleDisabled = false
        var playCoreUsed = false

        for ((_, map) in partialResults) {
            if (map.containsKey(KEY_LOCALE_CHANGE_LOCATION)) {
                if (localeChangeLocation == null) {
                    localeChangeLocation = map.getLocation(KEY_LOCALE_CHANGE_LOCATION)
                }
            }
            if (map.getBoolean(KEY_SPLITS_LOCALE_DISABLED) == true) {
                splitsLocaleDisabled = true
            }
            if (map.getBoolean(KEY_PLAY_CORE_USED) == true) {
                playCoreUsed = true
            }
        }

        // Only report if there's a locale change and neither mitigation is present
        if (localeChangeLocation != null && !splitsLocaleDisabled && !playCoreUsed) {
            val incident = Incident(
                ISSUE,
                localeChangeLocation,
                "Found a runtime locale change, but the app bundle is configured to split by " +
                    "locale (the default). This can cause crashes when resources for the new " +
                    "locale are not available. Either disable locale splits in your Gradle " +
                    "configuration (`splits { language { enable false } }`) or use the Play " +
                    "Core library to download the required locale resources at runtime. " +
                    "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
            )
            context.report(incident)
        }
    }
}