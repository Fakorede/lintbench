package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), Detector.SourceCodeScanner, Detector.GradleScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        // Keys used in LintMap for partial results
        private const val KEY_LOCALE_CHANGE_DETECTED = "localeChangeDetected"
        private const val KEY_LOCALE_CHANGE_LOCATION = "localeChangeLocation"
        private const val KEY_SPLITS_LANGUAGE_DISABLED = "splitsLanguageDisabled"
        private const val KEY_PLAY_CORE_USED = "playCoreUsed"

        // Methods that indicate runtime locale changes
        private val LOCALE_CHANGE_METHODS = listOf(
            "setLocale",
            "setLocales",
            "updateConfiguration",
            "applyOverrideConfiguration",
            "setDefaultLocale",
        )

        // Reference names that indicate locale-related operations
        private val LOCALE_REFERENCE_NAMES = listOf(
            "Locale",
            "LocaleList",
            "LocaleListCompat",
        )

        // Play Core SplitInstallManager methods for locale downloads
        private val PLAY_CORE_METHODS = listOf(
            "requestInstall",
            "startInstall",
            "deferredInstall",
        )

        // Classes associated with locale changes
        private val LOCALE_CHANGE_CLASSES = listOf(
            "java.util.Locale",
            "android.os.LocaleList",
            "androidx.core.os.LocaleListCompat",
            "android.content.res.Configuration",
        )

        // Play Core classes
        private val PLAY_CORE_CLASSES = listOf(
            "com.google.android.play.core.splitinstall.SplitInstallManager",
            "com.google.android.play.core.splitinstall.SplitInstallRequest",
            "com.google.android.play.core.splitcompat.SplitCompat",
        )
    }

    override fun getApplicableReferenceNames(): List<String> = LOCALE_REFERENCE_NAMES

    override fun getApplicableMethodNames(): List<String> =
        LOCALE_CHANGE_METHODS + PLAY_CORE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: ""

        // Check if this is a Play Core locale install call
        if (methodName in PLAY_CORE_METHODS) {
            if (PLAY_CORE_CLASSES.any { containingClass.contains(it) } ||
                containingClass.contains("SplitInstall")
            ) {
                val lintMap = context.getPartialResults(ISSUE).map()
                lintMap.put(KEY_PLAY_CORE_USED, true)
                return
            }
        }

        // Check if this is a locale change method call
        if (methodName in LOCALE_CHANGE_METHODS) {
            val isLocaleRelated = LOCALE_CHANGE_CLASSES.any { containingClass.contains(it) } ||
                containingClass.contains("Locale") ||
                containingClass.contains("Configuration") ||
                methodName == "setLocale" ||
                methodName == "setLocales" ||
                methodName == "setDefaultLocale"

            if (isLocaleRelated) {
                val lintMap = context.getPartialResults(ISSUE).map()
                if (!lintMap.getBoolean(KEY_LOCALE_CHANGE_DETECTED, false)) {
                    lintMap.put(KEY_LOCALE_CHANGE_DETECTED, true)
                    lintMap.put(KEY_LOCALE_CHANGE_LOCATION, context.getLocation(node))
                }
            }
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        // We detect locale usage via reference names like Locale, LocaleList, LocaleListCompat
        // This supplements method call detection for cases where locale objects are referenced
        // but we primarily rely on method calls for accuracy; here we note locale usage
        // We don't mark as locale change just from a reference - that would be too noisy
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        valueCookie: Any,
        statementCookie: Any,
    ) {
        // Check for splits { language { enable false } } in build.gradle
        // which disables language splitting in App Bundle
        if (property == "enable" && parent == "language" && parentParent == "splits") {
            if (value == "false") {
                val lintMap = context.getPartialResults(ISSUE).map()
                lintMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
            }
        }

        // Also check for bundle { language { enableSplit = false } }
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            if (value == "false") {
                val lintMap = context.getPartialResults(ISSUE).map()
                lintMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkAndReport(context, context.getPartialResults(ISSUE).map())
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val mergedMap = LintMap()

        // Merge all partial results
        for (map in partialResults) {
            if (map.getBoolean(KEY_LOCALE_CHANGE_DETECTED, false)) {
                mergedMap.put(KEY_LOCALE_CHANGE_DETECTED, true)
                val location = map.getLocation(KEY_LOCALE_CHANGE_LOCATION)
                if (location != null && !mergedMap.containsKey(KEY_LOCALE_CHANGE_LOCATION)) {
                    mergedMap.put(KEY_LOCALE_CHANGE_LOCATION, location)
                }
            }
            if (map.getBoolean(KEY_SPLITS_LANGUAGE_DISABLED, false)) {
                mergedMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
            }
            if (map.getBoolean(KEY_PLAY_CORE_USED, false)) {
                mergedMap.put(KEY_PLAY_CORE_USED, true)
            }
        }

        checkAndReport(context, mergedMap)
    }

    private fun checkAndReport(context: Context, map: LintMap) {
        val localeChangeDetected = map.getBoolean(KEY_LOCALE_CHANGE_DETECTED, false)
        val splitsLanguageDisabled = map.getBoolean(KEY_SPLITS_LANGUAGE_DISABLED, false)
        val playCoreUsed = map.getBoolean(KEY_PLAY_CORE_USED, false)

        // Only report if locale changes are detected but no mitigation is in place
        if (localeChangeDetected && !splitsLanguageDisabled && !playCoreUsed) {
            val location = map.getLocation(KEY_LOCALE_CHANGE_LOCATION)
            val message = "Found dynamic locale changes, but the app is using App Bundles. " +
                "If the app dynamically changes the locale, it needs to either configure the " +
                "App Bundle to not split by locale (add `bundle { language { enableSplit = false } }` " +
                "in your Gradle file) or use the Play Core library to download additional " +
                "language splits at runtime."

            if (location != null) {
                context.report(ISSUE, location, message)
            } else {
                context.report(ISSUE, context.project.dir, message)
            }
        }
    }
}