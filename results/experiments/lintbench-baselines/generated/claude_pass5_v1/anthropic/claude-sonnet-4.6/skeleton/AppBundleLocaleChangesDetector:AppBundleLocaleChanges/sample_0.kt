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

        // Keys used in LintMap for partial results
        private const val KEY_LOCALE_CHANGE_DETECTED = "localeChangeDetected"
        private const val KEY_LOCALE_CHANGE_LOCATION = "localeChangeLocation"
        private const val KEY_SPLITS_LANGUAGE_DISABLED = "splitsLanguageDisabled"
        private const val KEY_PLAY_CORE_USED = "playCoreUsed"

        // Methods that indicate locale/language changes at runtime
        private val LOCALE_CHANGE_METHODS = listOf(
            "setLocale",
            "setLocales",
            "applyOverrideConfiguration",
            "setDefaultLocale",
            "setDefault",
        )

        // Reference names that indicate locale/language changes
        private val LOCALE_REFERENCE_NAMES = listOf(
            "LOCALE_SERVICE",
        )

        // Play Core SplitInstallManager related method names
        private val PLAY_CORE_METHODS = listOf(
            "requestInstall",
            "startConfirmationDialogForResult",
            "deferredInstall",
            "cancelInstall",
        )

        // Play Core class names
        private const val SPLIT_INSTALL_REQUEST_CLASS = "SplitInstallRequest"
        private const val SPLIT_INSTALL_REQUEST_BUILDER_CLASS = "SplitInstallRequest.Builder"

        // Gradle property names
        private const val PROPERTY_LANGUAGE = "language"
        private const val PROPERTY_ENABLE = "enable"
        private const val PARENT_LANGUAGE = "language"
        private const val PARENT_SPLITS = "splits"
        private const val PARENT_BUNDLE = "bundle"
        private const val PARENT_BUNDLE_LANGUAGE = "language"
    }

    override fun getApplicableReferenceNames(): List<String> = LOCALE_REFERENCE_NAMES

    override fun getApplicableMethodNames(): List<String> = LOCALE_CHANGE_METHODS + PLAY_CORE_METHODS

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name

        if (methodName in PLAY_CORE_METHODS) {
            // Check if this is related to SplitInstallRequest (Play Core language download)
            val containingClass = method.containingClass?.qualifiedName ?: ""
            if (containingClass.contains("SplitInstallManager") ||
                containingClass.contains("SplitInstall")) {
                // Play Core is being used for language downloads
                val lintMap = context.getPartialResults(ISSUE).map()
                lintMap.put(KEY_PLAY_CORE_USED, true)
                return
            }
        }

        if (methodName in LOCALE_CHANGE_METHODS) {
            val containingClass = method.containingClass
            val qualifiedName = containingClass?.qualifiedName ?: ""

            // Check for Locale.setDefault()
            if (methodName == "setDefault" && qualifiedName == "java.util.Locale") {
                recordLocaleChange(context, node)
                return
            }

            // Check for AppCompatDelegate.setApplicationLocales() or similar
            if (methodName == "setLocale" || methodName == "setLocales") {
                recordLocaleChange(context, node)
                return
            }

            // Check for applyOverrideConfiguration which can be used for locale changes
            if (methodName == "applyOverrideConfiguration") {
                recordLocaleChange(context, node)
                return
            }

            if (methodName == "setDefaultLocale") {
                recordLocaleChange(context, node)
                return
            }
        }
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        // Check for locale service references that might indicate runtime locale changes
        val name = reference.resolvedName ?: return
        if (name in LOCALE_REFERENCE_NAMES) {
            recordLocaleChange(context, reference)
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
        // Check for splits { language { enable false } } which disables language splitting
        if (property == PROPERTY_ENABLE &&
            parent == PARENT_LANGUAGE &&
            parentParent == PARENT_SPLITS) {
            if (value == "false") {
                val lintMap = context.getPartialResults(ISSUE).map()
                lintMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
            }
        }

        // Check for bundle { language { enableSplit false } }
        if (property == "enableSplit" &&
            parent == PARENT_BUNDLE_LANGUAGE &&
            parentParent == PARENT_BUNDLE) {
            if (value == "false") {
                val lintMap = context.getPartialResults(ISSUE).map()
                lintMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
            }
        }
    }

    private fun recordLocaleChange(context: JavaContext, node: Any) {
        val lintMap = context.getPartialResults(ISSUE).map()
        lintMap.put(KEY_LOCALE_CHANGE_DETECTED, true)

        // Store location info for reporting
        val location = when (node) {
            is UCallExpression -> context.getLocation(node)
            is UReferenceExpression -> context.getLocation(node)
            else -> return
        }
        lintMap.put(KEY_LOCALE_CHANGE_LOCATION, location)
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkAndReport(context, context.getPartialResults(ISSUE).map())
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val mergedMap = LintMap()

        var localeChangeDetected = false
        var splitsLanguageDisabled = false
        var playCoreUsed = false
        var localeChangeLocation: com.android.tools.lint.detector.api.Location? = null

        for ((_, map) in partialResults) {
            if (map.getBoolean(KEY_LOCALE_CHANGE_DETECTED) == true) {
                localeChangeDetected = true
                val loc = map.getLocation(KEY_LOCALE_CHANGE_LOCATION)
                if (loc != null) {
                    localeChangeLocation = loc
                }
            }
            if (map.getBoolean(KEY_SPLITS_LANGUAGE_DISABLED) == true) {
                splitsLanguageDisabled = true
            }
            if (map.getBoolean(KEY_PLAY_CORE_USED) == true) {
                playCoreUsed = true
            }
        }

        if (localeChangeDetected) {
            mergedMap.put(KEY_LOCALE_CHANGE_DETECTED, true)
            if (localeChangeLocation != null) {
                mergedMap.put(KEY_LOCALE_CHANGE_LOCATION, localeChangeLocation)
            }
        }
        if (splitsLanguageDisabled) {
            mergedMap.put(KEY_SPLITS_LANGUAGE_DISABLED, true)
        }
        if (playCoreUsed) {
            mergedMap.put(KEY_PLAY_CORE_USED, true)
        }

        checkAndReport(context, mergedMap)
    }

    private fun checkAndReport(context: Context, lintMap: LintMap) {
        val localeChangeDetected = lintMap.getBoolean(KEY_LOCALE_CHANGE_DETECTED) ?: false

        if (!localeChangeDetected) {
            return
        }

        val splitsLanguageDisabled = lintMap.getBoolean(KEY_SPLITS_LANGUAGE_DISABLED) ?: false
        val playCoreUsed = lintMap.getBoolean(KEY_PLAY_CORE_USED) ?: false

        // If either mitigation is in place, no warning needed
        if (splitsLanguageDisabled || playCoreUsed) {
            return
        }

        // Report the issue
        val location = lintMap.getLocation(KEY_LOCALE_CHANGE_LOCATION)
            ?: context.project.dir.let {
                com.android.tools.lint.detector.api.Location.create(it)
            }

        context.report(
            ISSUE,
            location,
            "Found a locale change at runtime, but the app bundle is not configured to " +
                "handle this. Either configure the bundle to not split by locale " +
                "(`bundle { language { enableSplit = false } }`) or use the Play Core " +
                "library to download additional locales at runtime.",
        )
    }
}