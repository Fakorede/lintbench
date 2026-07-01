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
        private const val KEY_LOCALE_CHANGE_LOCATIONS = "localeChangeLocations"
        private const val KEY_SPLITS_LANGUAGE_DISABLED = "splitsLanguageDisabled"
        private const val KEY_PLAY_CORE_USED = "playCoreUsed"

        private const val KEY_LOCATION_FILE = "file"
        private const val KEY_LOCATION_LINE = "line"
        private const val KEY_LOCATION_MESSAGE = "message"

        private val LOCALE_CHANGE_METHODS = listOf(
            "setLocale",
            "setLocales",
            "setDefault",
            "applyOverrideConfiguration",
            "updateConfiguration",
            "updateLocale"
        )

        private val LOCALE_REFERENCE_NAMES = listOf(
            "setLocale",
            "setLocales",
            "setDefault",
            "applyOverrideConfiguration",
            "updateConfiguration",
            "updateLocale"
        )

        private val PLAY_CORE_SPLIT_INSTALL_METHODS = listOf(
            "startInstall",
            "deferredInstall",
            "deferredLanguageInstall",
            "requestInstall"
        )

        private const val SPLIT_INSTALL_MANAGER = "com.google.android.play.core.splitinstall.SplitInstallManager"
        private const val SPLIT_INSTALL_REQUEST = "com.google.android.play.core.splitinstall.SplitInstallRequest"
        private const val SPLIT_COMPAT_MANAGER = "com.google.android.play.core.splitcompat.SplitCompat"

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation =
                """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale, or the Play Core \
                library must be used to download additional locales at runtime.

                If locale splits are enabled (the default), the resources for a given locale will \
                only be available if they were included in the base APK, or downloaded via the \
                Play Core library. Without one of these measures, your app may fail to find \
                resources in the new locale after a runtime locale change.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            ),
            androidSpecific = true
        )

        private const val KEY_INCIDENTS = "incidents"
        private const val INCIDENT_SEPARATOR = "||"
    }

    override fun getApplicableMethodNames(): List<String> =
        LOCALE_CHANGE_METHODS + PLAY_CORE_SPLIT_INSTALL_METHODS

    override fun getApplicableReferenceNames(): List<String> =
        LOCALE_REFERENCE_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name

        // Check if this is a Play Core split install call (means app handles locale downloads)
        if (methodName in PLAY_CORE_SPLIT_INSTALL_METHODS) {
            val containingClass = method.containingClass?.qualifiedName ?: ""
            if (containingClass == SPLIT_INSTALL_MANAGER ||
                containingClass.startsWith("com.google.android.play.core")
            ) {
                context.getPartialResults(ISSUE).map().put(KEY_PLAY_CORE_USED, true)
                return
            }
        }

        // Check if this is a locale change call
        if (methodName in LOCALE_CHANGE_METHODS) {
            if (isLocaleChangeCall(context, method)) {
                recordLocaleChangeIncident(context, node)
            }
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement
    ) {
        // Additional reference-based detection for locale changes
        val name = reference.resolvedName ?: return
        if (name in LOCALE_REFERENCE_NAMES) {
            if (referenced is PsiMethod && isLocaleChangeCall(context, referenced)) {
                recordLocaleChangeIncident(context, reference)
            }
        }
    }

    private fun isLocaleChangeCall(context: JavaContext, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        val containingClass = method.containingClass?.qualifiedName ?: return false

        return when (method.name) {
            "setLocale" -> {
                evaluator.isMemberInSubClassOf(method, "android.content.res.Configuration") ||
                    evaluator.isMemberInSubClassOf(method, "java.util.Locale") ||
                    containingClass.contains("LocaleHelper") ||
                    containingClass.contains("LocaleUtils") ||
                    containingClass.contains("LocaleManager")
            }
            "setLocales" -> {
                evaluator.isMemberInSubClassOf(method, "android.content.res.Configuration") ||
                    evaluator.isMemberInSubClassOf(method, "android.os.LocaleList")
            }
            "setDefault" -> {
                containingClass == "java.util.Locale" ||
                    evaluator.isMemberInSubClassOf(method, "java.util.Locale")
            }
            "applyOverrideConfiguration" -> {
                evaluator.isMemberInSubClassOf(method, "android.view.ContextThemeWrapper") ||
                    evaluator.isMemberInSubClassOf(method, "androidx.appcompat.app.AppCompatActivity")
            }
            "updateConfiguration" -> {
                evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")
            }
            "updateLocale" -> true
            else -> false
        }
    }

    private fun recordLocaleChangeIncident(
        context: JavaContext,
        node: org.jetbrains.uast.UElement
    ) {
        val location = context.getLocation(node)
        val map = context.getPartialResults(ISSUE).map()

        // Store incident location info
        val existingCount = map.getInt(KEY_LOCALE_CHANGE_LOCATIONS) ?: 0
        val idx = existingCount
        map.put("${KEY_LOCATION_FILE}_$idx", location.file.path)
        map.put("${KEY_LOCATION_LINE}_$idx", location.start?.line ?: 0)
        map.put(
            "${KEY_LOCATION_MESSAGE}_$idx",
            "Runtime locale changes should be used with App Bundle locale splits disabled " +
                "or Play Core library for on-demand locale downloads"
        )
        map.put(KEY_LOCALE_CHANGE_LOCATIONS, existingCount + 1)
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any
    ) {
        // Check for splits { language { enable false } } in Gradle
        if (property == "enable" && value == "false" && parent == "language") {
            // Language splits are disabled — record this
            context.getPartialResults(ISSUE).map().put(KEY_SPLITS_LANGUAGE_DISABLED, true)
            return
        }

        // Also check for bundle { language { enableSplit false } }
        if (property == "enableSplit" && value == "false" && parent == "language") {
            context.getPartialResults(ISSUE).map().put(KEY_SPLITS_LANGUAGE_DISABLED, true)
            return
        }

        // Check for bundle block: bundle { language { enableSplit = false } }
        if (property == "enableSplit" && value == "false" &&
            (parent == "language" || parentParent == "bundle")
        ) {
            context.getPartialResults(ISSUE).map().put(KEY_SPLITS_LANGUAGE_DISABLED, true)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // Nothing to do here; we handle reporting in checkPartialResults
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var hasLocaleChanges = false
        var splitsLanguageDisabled = false
        var playCoreUsed = false

        // Aggregate results from all modules
        partialResults.forEach { _, map ->
            val localeChangeCount = map.getInt(KEY_LOCALE_CHANGE_LOCATIONS) ?: 0
            if (localeChangeCount > 0) {
                hasLocaleChanges = true
            }
            if (map.getBoolean(KEY_SPLITS_LANGUAGE_DISABLED) == true) {
                splitsLanguageDisabled = true
            }
            if (map.getBoolean(KEY_PLAY_CORE_USED) == true) {
                playCoreUsed = true
            }
        }

        // If locale changes are detected but neither mitigation is in place, report
        if (hasLocaleChanges && !splitsLanguageDisabled && !playCoreUsed) {
            partialResults.forEach { _, map ->
                val localeChangeCount = map.getInt(KEY_LOCALE_CHANGE_LOCATIONS) ?: 0
                for (i in 0 until localeChangeCount) {
                    val filePath = map.getString("${KEY_LOCATION_FILE}_$i") ?: continue
                    val line = map.getInt("${KEY_LOCATION_LINE}_$i") ?: 0
                    val message = map.getString("${KEY_LOCATION_MESSAGE}_$i")
                        ?: "Runtime locale changes should be used with App Bundle locale splits " +
                            "disabled or Play Core library for on-demand locale downloads"

                    val file = java.io.File(filePath)
                    val location = Location.create(file)
                    val incident = Incident(ISSUE, location, message)
                    context.report(incident)
                }
            }
        }
    }
}