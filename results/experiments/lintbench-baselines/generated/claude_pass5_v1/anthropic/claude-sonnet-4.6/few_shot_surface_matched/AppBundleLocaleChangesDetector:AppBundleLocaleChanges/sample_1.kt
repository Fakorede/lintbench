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
        private const val KEY_SPLITS_LANGUAGE_DISABLED = "splitsLanguageDisabled"
        private const val KEY_LOCALE_CHANGE_FILE = "localeChangeFile"
        private const val KEY_LOCALE_CHANGE_LINE = "localeChangeLine"

        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val LOCALE_CLASS = "java.util.Locale"
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val LOCALE_LIST_CLASS = "android.os.LocaleList"
        private const val APP_COMPAT_RESOURCES = "androidx.appcompat.app.AppCompatDelegate"

        // Play Core / Play In-App Languages API
        private const val SPLIT_INSTALL_MANAGER = "com.google.android.play.core.splitinstall.SplitInstallManager"
        private const val SPLIT_INSTALL_MANAGER_FACTORY = "com.google.android.play.core.splitinstall.SplitInstallManagerFactory"

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation =
                """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale, or the Play Core \
                library must be used to download additional locales at runtime.

                If the app bundle splits by locale (the default), changing the locale at runtime \
                may result in a crash because the resources for the new locale have not been \
                downloaded.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
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

        // Method names that indicate a locale change at runtime
        private val LOCALE_CHANGE_METHODS = listOf(
            "setLocale",
            "setLocales",
            "setApplicationLocales",
            "updateConfiguration",
            "applyOverrideConfiguration"
        )

        // Reference names that indicate locale-related usage
        private val LOCALE_REFERENCE_NAMES = listOf(
            "locale",
            "locales",
            "LOCALE_SERVICE"
        )
    }

    // Track whether Play Core split install is used in the project
    private var usesSplitInstallManager = false

    override fun getApplicableMethodNames(): List<String> = LOCALE_CHANGE_METHODS

    override fun getApplicableReferenceNames(): List<String> = LOCALE_REFERENCE_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        when (methodName) {
            "setLocale", "setLocales" -> {
                val containingClass = method.containingClass?.qualifiedName ?: return
                if (containingClass == CONFIGURATION_CLASS ||
                    containingClass == LOCALE_LIST_CLASS ||
                    context.evaluator.extendsClass(
                        method.containingClass,
                        CONFIGURATION_CLASS,
                        true
                    )
                ) {
                    reportLocaleChange(context, node)
                }
            }
            "setApplicationLocales" -> {
                val containingClass = method.containingClass?.qualifiedName ?: return
                if (containingClass == APP_COMPAT_RESOURCES ||
                    containingClass.contains("LocaleManagerCompat") ||
                    containingClass.contains("LocaleManager")
                ) {
                    reportLocaleChange(context, node)
                }
            }
            "updateConfiguration" -> {
                val containingClass = method.containingClass?.qualifiedName ?: return
                if (containingClass == RESOURCES_CLASS ||
                    context.evaluator.extendsClass(
                        method.containingClass,
                        RESOURCES_CLASS,
                        true
                    )
                ) {
                    // Check if the configuration being set has a locale
                    reportLocaleChange(context, node)
                }
            }
            "applyOverrideConfiguration" -> {
                reportLocaleChange(context, node)
            }
            "create" -> {
                val containingClass = method.containingClass?.qualifiedName ?: return
                if (containingClass == SPLIT_INSTALL_MANAGER_FACTORY) {
                    markUsesSplitInstallManager(context)
                }
            }
        }

        // Check for Play Core SplitInstallManager usage
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (containingClass == SPLIT_INSTALL_MANAGER ||
            context.evaluator.implementsInterface(
                method.containingClass,
                SPLIT_INSTALL_MANAGER,
                false
            )
        ) {
            markUsesSplitInstallManager(context)
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement
    ) {
        // Additional reference-based detection can be done here if needed
        // For example, detecting direct field access related to locale configuration
        val name = reference.resolvedName ?: return
        if (name == "LOCALE_SERVICE") {
            // android.app.LocaleManager access — locale change might happen
            // Only flag if this is the actual system service constant
            if (referenced is com.intellij.psi.PsiField) {
                val containingClass = (referenced as com.intellij.psi.PsiField).containingClass
                if (containingClass?.qualifiedName == "android.content.Context") {
                    reportLocaleChange(context, reference)
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
        // Check for splits { language { enable false } } in build.gradle
        // This means the app is configured to not split by locale — safe!
        if (property == "enable" && value == "false" && parent == "language" &&
            parentParent == "splits"
        ) {
            val lintMap = context.getPartialResults(ISSUE).map()
            lintMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
        }

        // Also check for bundle { language { enableSplit false } }
        if (property == "enableSplit" && value == "false" && parent == "language" &&
            (parentParent == "bundle" || parentParent == null)
        ) {
            val lintMap = context.getPartialResults(ISSUE).map()
            lintMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
        }
    }

    private fun reportLocaleChange(context: JavaContext, node: org.jetbrains.uast.UElement) {
        val location = context.getLocation(node)
        val lintMap = context.getPartialResults(ISSUE).map()

        // Store location info for deferred reporting
        val existingCount = lintMap.getInt(KEY_LOCALE_CHANGE_LINE, -1)
        if (existingCount == -1) {
            // Store the first occurrence
            lintMap.put(KEY_LOCALE_CHANGE_FILE, location.file.path)
            lintMap.put(KEY_LOCALE_CHANGE_LINE, location.start?.line ?: 0)
            // Store a serializable representation of the location
            lintMap.put(KEY_LOCALE_CHANGE_LOCATION, location.file.path + ":" + (location.start?.line ?: 0))
        }
    }

    private fun markUsesSplitInstallManager(context: JavaContext) {
        val lintMap = context.getPartialResults(ISSUE).map()
        lintMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
    }

    override fun afterCheckEachProject(context: Context) {
        // In non-partial analysis mode, check the collected data
        if (context.isGlobalAnalysis()) {
            val lintMap = context.getPartialResults(ISSUE).map()
            checkResults(context, lintMap)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val mergedMap = LintMap()

        var hasLocaleChange = false
        var splitsLanguageDisabled = false
        var localeChangeLocation: String? = null

        for ((_, map) in partialResults) {
            if (map.getBoolean(KEY_SPLITS_LANGUAGE_DISABLED, false)) {
                splitsLanguageDisabled = true
            }
            val locationStr = map.getString(KEY_LOCALE_CHANGE_LOCATION, null)
            if (locationStr != null) {
                hasLocaleChange = true
                if (localeChangeLocation == null) {
                    localeChangeLocation = locationStr
                }
            }
        }

        if (hasLocaleChange) {
            mergedMap.put(KEY_LOCALE_CHANGE_LOCATION, localeChangeLocation ?: "")
        }
        if (splitsLanguageDisabled) {
            mergedMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
        }

        checkResults(context, mergedMap)
    }

    private fun checkResults(context: Context, map: LintMap) {
        val splitsLanguageDisabled = map.getBoolean(KEY_SPLITS_LANGUAGE_DISABLED, false)
        val localeChangeLocation = map.getString(KEY_LOCALE_CHANGE_LOCATION, null)

        if (localeChangeLocation != null && !splitsLanguageDisabled) {
            // We have a locale change but no configuration to handle it safely
            val locationParts = localeChangeLocation.split(":")
            val filePath = locationParts.dropLast(1).joinToString(":")
            val line = locationParts.lastOrNull()?.toIntOrNull() ?: 0

            val file = java.io.File(filePath)
            val location = if (file.exists()) {
                Location.create(file, null, line)
            } else {
                Location.create(context.project.dir)
            }

            context.report(
                Incident(
                    ISSUE,
                    location,
                    "App Bundle splits by locale by default. If you change the locale at " +
                        "runtime, the resources for the new locale may not be available. " +
                        "Either configure the app bundle to not split by locale " +
                        "(`bundle { language { enableSplit = false } }`) or use the " +
                        "Play Core library to download locales on demand. " +
                        "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
                )
            )
        }
    }
}