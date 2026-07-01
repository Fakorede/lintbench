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

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

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

        // Keys used in the LintMap for partial results
        private const val KEY_LOCALE_CHANGE_DETECTED = "localeChangeDetected"
        private const val KEY_LOCALE_CHANGE_LOCATION = "localeChangeLocation"
        private const val KEY_SPLITS_LANGUAGE_DISABLED = "splitsLanguageDisabled"
        private const val KEY_PLAY_CORE_USED = "playCoreUsed"

        // Method names that indicate a locale/language change at runtime
        private val LOCALE_CHANGE_METHODS = listOf(
            "setLocale",
            "setLocales",
            "setDefault",
            "applyLanguage",
            "applyLocale",
            "setLanguage",
            "updateConfiguration",
            "setApplicationLocales",
        )

        // Reference names that indicate locale usage
        private val LOCALE_REFERENCE_NAMES = listOf(
            "setApplicationLocales",
        )

        // Play Core SplitInstallManager methods related to language installs
        private val PLAY_CORE_METHODS = listOf(
            "startInstall",
            "installLanguages",
        )

        // Classes related to locale/configuration changes
        private val LOCALE_RELATED_CLASSES = setOf(
            "java.util.Locale",
            "android.os.LocaleList",
            "androidx.core.os.LocaleListCompat",
            "android.content.res.Configuration",
            "androidx.appcompat.app.AppCompatDelegate",
            "android.app.LocaleManager",
        )

        // Play Core related classes
        private val PLAY_CORE_CLASSES = setOf(
            "com.google.android.play.core.splitinstall.SplitInstallManager",
            "com.google.android.play.core.splitinstall.SplitInstallRequest",
            "com.google.android.play.core.splitinstall.SplitInstallManagerFactory",
        )
    }

    override fun getApplicableReferenceNames(): List<String> = LOCALE_REFERENCE_NAMES

    override fun getApplicableMethodNames(): List<String> = LOCALE_CHANGE_METHODS + PLAY_CORE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        val methodName = method.name

        // Check if this is a Play Core language install call
        if (methodName in PLAY_CORE_METHODS && containingClass in PLAY_CORE_CLASSES) {
            val lintMap = context.getPartialResults(ISSUE).map()
            lintMap.put(KEY_PLAY_CORE_USED, true)
            return
        }

        // Check if this is a locale change method call
        if (methodName in LOCALE_CHANGE_METHODS && containingClass in LOCALE_RELATED_CLASSES) {
            val lintMap = context.getPartialResults(ISSUE).map()
            if (!lintMap.getBoolean(KEY_LOCALE_CHANGE_DETECTED, false)) {
                lintMap.put(KEY_LOCALE_CHANGE_DETECTED, true)
                val location = context.getLocation(node)
                lintMap.put(KEY_LOCALE_CHANGE_LOCATION, location)
            }
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        // Additional reference-based detection if needed
        val lintMap = context.getPartialResults(ISSUE).map()
        if (!lintMap.getBoolean(KEY_LOCALE_CHANGE_DETECTED, false)) {
            lintMap.put(KEY_LOCALE_CHANGE_DETECTED, true)
            val location = context.getLocation(reference)
            lintMap.put(KEY_LOCALE_CHANGE_LOCATION, location)
        }
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
        // Check for: bundle { language { enableSplit = false } }
        // or splits { language { enable false } }
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            if (value == "false") {
                val lintMap = context.getPartialResults(ISSUE).map()
                lintMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
            }
        }

        if (property == "enable" && parent == "language" && parentParent == "splits") {
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

        // Merge all partial results from different modules/files
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
        if (!localeChangeDetected) {
            // No locale change detected, nothing to warn about
            return
        }

        val splitsLanguageDisabled = map.getBoolean(KEY_SPLITS_LANGUAGE_DISABLED, false)
        val playCoreUsed = map.getBoolean(KEY_PLAY_CORE_USED, false)

        if (splitsLanguageDisabled || playCoreUsed) {
            // The app has properly handled locale changes
            return
        }

        // Report the issue: locale changes detected but no proper App Bundle configuration found
        val location = map.getLocation(KEY_LOCALE_CHANGE_LOCATION)
        val message = "Found a locale change, but the app is not handling App Bundle locale " +
            "splits properly. Either configure the App Bundle to not split by locale " +
            "(`bundle { language { enableSplit = false } }` in your Gradle file) or use " +
            "the Play Core library to download locales at runtime."

        if (location != null) {
            context.report(ISSUE, location, message)
        } else {
            context.report(
                ISSUE,
                context.project.dir,
                null,
                message,
            )
        }
    }
}