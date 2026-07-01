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

        // Key used to store that a locale change was detected in partial results
        private const val KEY_LOCALE_CHANGE_DETECTED = "localeChangeDetected"

        // Key used to store that locale splits are disabled in partial results
        private const val KEY_LOCALE_SPLITS_DISABLED = "localeSplitsDisabled"

        // Key used to store that Play Core SplitInstallManager is used
        private const val KEY_SPLIT_INSTALL_MANAGER_USED = "splitInstallManagerUsed"

        // Key for storing location information
        private const val KEY_LOCALE_CHANGE_LOCATION = "localeChangeLocation"

        // Methods that indicate runtime locale changes
        private val LOCALE_CHANGE_METHODS = listOf(
            "setLocale",
            "setLocales",
            "setDefault",
            "applyOverrideConfiguration",
            "setApplicationLocales",
            "setSystemLocales",
        )

        // Reference names that indicate locale handling
        private val LOCALE_REFERENCE_NAMES = listOf(
            "LOCALE_SERVICE",
            "LocaleList",
            "LocaleListCompat",
        )

        // Class names that contain locale change methods
        private val LOCALE_CLASS_NAMES = setOf(
            "java.util.Locale",
            "android.os.LocaleList",
            "androidx.core.os.LocaleListCompat",
            "android.content.res.Configuration",
            "androidx.appcompat.app.AppCompatDelegate",
            "android.app.LocaleManager",
        )

        // Play Core SplitInstallManager methods for requesting language installs
        private val SPLIT_INSTALL_METHODS = listOf(
            "startInstall",
            "requestInstall",
        )

        // Play Core SplitInstallManager class
        private const val SPLIT_INSTALL_MANAGER_CLASS = "com.google.android.play.core.splitinstall.SplitInstallManager"
        private const val SPLIT_INSTALL_REQUEST_CLASS = "com.google.android.play.core.splitinstall.SplitInstallRequest"
        private const val SPLIT_INSTALL_MANAGER_FACTORY_CLASS = "com.google.android.play.core.splitinstall.SplitInstallManagerFactory"
    }

    override fun getApplicableReferenceNames(): List<String> = LOCALE_REFERENCE_NAMES

    override fun getApplicableMethodNames(): List<String> =
        LOCALE_CHANGE_METHODS + SPLIT_INSTALL_METHODS + listOf("create", "newBuilder")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: ""

        // Check if this is a Play Core SplitInstallManager usage
        if (isSplitInstallManagerUsage(methodName, containingClass)) {
            val lintMap = context.getPartialResults(ISSUE).map()
            lintMap.put(KEY_SPLIT_INSTALL_MANAGER_USED, true)
            return
        }

        // Check if this is a locale change call
        if (isLocaleChangeCall(methodName, containingClass)) {
            val lintMap = context.getPartialResults(ISSUE).map()
            if (!lintMap.getBoolean(KEY_LOCALE_CHANGE_DETECTED, false)) {
                lintMap.put(KEY_LOCALE_CHANGE_DETECTED, true)
                // Store location info for reporting later
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
        val name = reference.resolvedName ?: return

        if (name in LOCALE_REFERENCE_NAMES) {
            // This could indicate locale usage - check the containing class context
            val lintMap = context.getPartialResults(ISSUE).map()
            if (!lintMap.getBoolean(KEY_LOCALE_CHANGE_DETECTED, false)) {
                // Only flag as potential locale change if it's a meaningful reference
                // We record it but don't report yet - we'll check in afterCheckEachProject
                lintMap.put(KEY_LOCALE_CHANGE_DETECTED, true)
                val location = context.getLocation(reference)
                lintMap.put(KEY_LOCALE_CHANGE_LOCATION, location)
            }
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
        // Check for bundle { language { enableSplit = false } } in build.gradle
        // This disables locale splitting for App Bundles
        if (property == "enableSplit" && parent == "language") {
            val lintMap = context.getPartialResults(ISSUE).map()
            if (value == "false") {
                lintMap.put(KEY_LOCALE_SPLITS_DISABLED, true)
            }
        }

        // Also check for: splits { language { enable false } }
        if (property == "enable" && parent == "language" && parentParent == "splits") {
            val lintMap = context.getPartialResults(ISSUE).map()
            if (value == "false") {
                lintMap.put(KEY_LOCALE_SPLITS_DISABLED, true)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkAndReport(context, context.getPartialResults(ISSUE).map())
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Merge results from all modules
        val mergedMap = context.getPartialResults(ISSUE).map()

        for ((_, map) in partialResults) {
            if (map.getBoolean(KEY_LOCALE_CHANGE_DETECTED, false)) {
                mergedMap.put(KEY_LOCALE_CHANGE_DETECTED, true)
                // Preserve location if not already set
                if (!mergedMap.containsKey(KEY_LOCALE_CHANGE_LOCATION)) {
                    val location = map.getLocation(KEY_LOCALE_CHANGE_LOCATION)
                    if (location != null) {
                        mergedMap.put(KEY_LOCALE_CHANGE_LOCATION, location)
                    }
                }
            }
            if (map.getBoolean(KEY_LOCALE_SPLITS_DISABLED, false)) {
                mergedMap.put(KEY_LOCALE_SPLITS_DISABLED, true)
            }
            if (map.getBoolean(KEY_SPLIT_INSTALL_MANAGER_USED, false)) {
                mergedMap.put(KEY_SPLIT_INSTALL_MANAGER_USED, true)
            }
        }

        checkAndReport(context, mergedMap)
    }

    private fun checkAndReport(context: Context, map: LintMap) {
        val localeChangeDetected = map.getBoolean(KEY_LOCALE_CHANGE_DETECTED, false)
        val localeSplitsDisabled = map.getBoolean(KEY_LOCALE_SPLITS_DISABLED, false)
        val splitInstallManagerUsed = map.getBoolean(KEY_SPLIT_INSTALL_MANAGER_USED, false)

        // If locale changes are detected and neither mitigation is in place, report
        if (localeChangeDetected && !localeSplitsDisabled && !splitInstallManagerUsed) {
            val location = map.getLocation(KEY_LOCALE_CHANGE_LOCATION)
            if (location != null) {
                context.report(
                    ISSUE,
                    location,
                    """
                        Found a locale change at runtime. If you use App Bundles, the app may \
                        not have the required locale resources available. To fix this, either \
                        configure your App Bundle to not split by locale \
                        (`bundle { language { enableSplit = false } }`) or use the Play Core \
                        library to download additional locales at runtime.
                    """.trimIndent(),
                )
            } else {
                context.report(
                    ISSUE,
                    context.project.dir,
                    """
                        Found a locale change at runtime. If you use App Bundles, the app may \
                        not have the required locale resources available. To fix this, either \
                        configure your App Bundle to not split by locale \
                        (`bundle { language { enableSplit = false } }`) or use the Play Core \
                        library to download additional locales at runtime.
                    """.trimIndent(),
                )
            }
        }
    }

    private fun isLocaleChangeCall(methodName: String, containingClass: String): Boolean {
        if (methodName !in LOCALE_CHANGE_METHODS) return false
        // Check if the method is in a relevant class
        return LOCALE_CLASS_NAMES.any { containingClass.startsWith(it) } ||
            containingClass.contains("Locale", ignoreCase = true) ||
            containingClass.contains("Configuration", ignoreCase = true)
    }

    private fun isSplitInstallManagerUsage(methodName: String, containingClass: String): Boolean {
        return (containingClass == SPLIT_INSTALL_MANAGER_CLASS ||
            containingClass == SPLIT_INSTALL_REQUEST_CLASS ||
            containingClass == SPLIT_INSTALL_MANAGER_FACTORY_CLASS ||
            containingClass.contains("SplitInstall", ignoreCase = true)) &&
            (methodName in SPLIT_INSTALL_METHODS || methodName == "create" || methodName == "newBuilder")
    }
}