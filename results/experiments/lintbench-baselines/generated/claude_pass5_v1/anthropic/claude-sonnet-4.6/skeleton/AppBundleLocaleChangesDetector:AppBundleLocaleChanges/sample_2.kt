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

        // Key used in the LintMap to store whether locale changes were detected
        private const val KEY_LOCALE_CHANGE_DETECTED = "localeChangeDetected"
        // Key used in the LintMap to store whether locale splits are disabled
        private const val KEY_LOCALE_SPLITS_DISABLED = "localeSplitsDisabled"
        // Key used in the LintMap to store whether Play Core SplitInstallManager is used
        private const val KEY_SPLIT_INSTALL_MANAGER_USED = "splitInstallManagerUsed"
        // Key for storing the location info for reporting
        private const val KEY_LOCATION = "location"

        // Methods that indicate locale changes at runtime
        private val LOCALE_CHANGE_METHODS = listOf(
            "setLocale",
            "setLocales",
            "setDefaultLocale",
            "applyOverrideConfiguration",
        )

        // Reference names that indicate locale changes
        private val LOCALE_CHANGE_REFERENCES = listOf(
            "locale",
            "locales",
        )

        // Gradle property names related to locale splits
        private const val PROP_LANGUAGE = "language"
        private const val PROP_ENABLE = "enable"

        // Play Core SplitInstallManager method
        private const val METHOD_GET_SPLIT_INSTALL_MANAGER = "create"

        // Class names for locale-related APIs
        private const val CLASS_LOCALE = "java.util.Locale"
        private const val CLASS_LOCALE_LIST = "android.os.LocaleList"
        private const val CLASS_CONFIGURATION = "android.content.res.Configuration"
        private const val CLASS_APP_COMPAT_DELEGATE = "androidx.appcompat.app.AppCompatDelegate"
        private const val CLASS_SPLIT_INSTALL_MANAGER_FACTORY = "com.google.android.play.core.splitinstall.SplitInstallManagerFactory"

        // AppCompatDelegate locale methods
        private val APP_COMPAT_DELEGATE_LOCALE_METHODS = listOf(
            "setApplicationLocales",
        )
    }

    override fun getApplicableReferenceNames(): List<String> {
        return LOCALE_CHANGE_REFERENCES
    }

    override fun getApplicableMethodNames(): List<String> {
        return LOCALE_CHANGE_METHODS + APP_COMPAT_DELEGATE_LOCALE_METHODS + listOf(METHOD_GET_SPLIT_INSTALL_MANAGER)
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: ""

        // Check if this is a SplitInstallManagerFactory.create() call (Play Core usage)
        if (methodName == METHOD_GET_SPLIT_INSTALL_MANAGER &&
            containingClass == CLASS_SPLIT_INSTALL_MANAGER_FACTORY) {
            val map = context.getPartialResults(ISSUE).map()
            map.put(KEY_SPLIT_INSTALL_MANAGER_USED, true)
            return
        }

        // Check if this is AppCompatDelegate.setApplicationLocales()
        if (methodName in APP_COMPAT_DELEGATE_LOCALE_METHODS &&
            containingClass == CLASS_APP_COMPAT_DELEGATE) {
            val map = context.getPartialResults(ISSUE).map()
            map.put(KEY_LOCALE_CHANGE_DETECTED, true)
            val location = context.getLocation(node)
            map.put(KEY_LOCATION, location)
            return
        }

        // Check for Configuration.setLocale / setLocales
        if (methodName in LOCALE_CHANGE_METHODS) {
            if (containingClass == CLASS_CONFIGURATION ||
                containingClass == CLASS_LOCALE ||
                containingClass == CLASS_LOCALE_LIST) {
                val map = context.getPartialResults(ISSUE).map()
                map.put(KEY_LOCALE_CHANGE_DETECTED, true)
                val location = context.getLocation(node)
                map.put(KEY_LOCATION, location)
            }
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        // Additional reference-based detection if needed
        // For now, the primary detection is via method calls
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
        // Look for bundle { language { enableSplit = false } } configuration
        // Structure: enableSplit inside language inside bundle
        if (property == PROP_ENABLE && parent == PROP_LANGUAGE) {
            val map = context.getPartialResults(ISSUE).map()
            if (value == "false") {
                map.put(KEY_LOCALE_SPLITS_DISABLED, true)
            } else {
                // enableSplit = true means splits are enabled (default behavior)
                map.put(KEY_LOCALE_SPLITS_DISABLED, false)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkAndReport(context, context.getPartialResults(ISSUE).map())
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Merge partial results from all modules
        val mergedMap = LintMap()
        var localeChangeDetected = false
        var localeSplitsDisabled = false
        var splitInstallManagerUsed = false

        for ((_, map) in partialResults) {
            if (map.getBoolean(KEY_LOCALE_CHANGE_DETECTED) == true) {
                localeChangeDetected = true
                // Copy location if available
                val loc = map.getLocation(KEY_LOCATION)
                if (loc != null) {
                    mergedMap.put(KEY_LOCATION, loc)
                }
            }
            if (map.getBoolean(KEY_LOCALE_SPLITS_DISABLED) == true) {
                localeSplitsDisabled = true
            }
            if (map.getBoolean(KEY_SPLIT_INSTALL_MANAGER_USED) == true) {
                splitInstallManagerUsed = true
            }
        }

        if (localeChangeDetected) {
            mergedMap.put(KEY_LOCALE_CHANGE_DETECTED, true)
        }
        if (localeSplitsDisabled) {
            mergedMap.put(KEY_LOCALE_SPLITS_DISABLED, true)
        }
        if (splitInstallManagerUsed) {
            mergedMap.put(KEY_SPLIT_INSTALL_MANAGER_USED, true)
        }

        checkAndReport(context, mergedMap)
    }

    private fun checkAndReport(context: Context, map: LintMap) {
        val localeChangeDetected = map.getBoolean(KEY_LOCALE_CHANGE_DETECTED) ?: false
        val localeSplitsDisabled = map.getBoolean(KEY_LOCALE_SPLITS_DISABLED) ?: false
        val splitInstallManagerUsed = map.getBoolean(KEY_SPLIT_INSTALL_MANAGER_USED) ?: false

        if (!localeChangeDetected) {
            return
        }

        // If locale splits are disabled or Play Core SplitInstallManager is used, no issue
        if (localeSplitsDisabled || splitInstallManagerUsed) {
            return
        }

        // Report the issue
        val location = map.getLocation(KEY_LOCATION)
        val message = "Found a locale change at runtime, but the app bundle is not configured " +
            "to handle this. Either configure the bundle to not split by locale " +
            "(`bundle { language { enableSplit = false } }`) or use the Play Core library " +
            "to download additional locales at runtime."

        if (location != null) {
            context.report(ISSUE, location, message)
        } else {
            context.report(ISSUE, context.project.dir, message)
        }
    }
}