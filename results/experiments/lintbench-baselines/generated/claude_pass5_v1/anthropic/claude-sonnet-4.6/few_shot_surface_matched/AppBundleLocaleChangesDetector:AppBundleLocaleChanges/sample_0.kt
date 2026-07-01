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
        private const val KEY_LOCALE_CHANGE = "localeChange"
        private const val KEY_SPLITS_LOCALE_DISABLED = "splitsLocaleDisabled"
        private const val KEY_PLAY_CORE_USED = "playCoreUsed"
        private const val KEY_LOCATION = "location"

        // Locale configuration methods in Java/Kotlin APIs
        private val LOCALE_METHODS = listOf(
            "setLocale",
            "setLocales",
            "setDefaultLocale",
            "applyOverrideConfiguration",
            "updateConfiguration",
            "updateLocale",
        )

        // Locale-related reference names (e.g. Locale.setDefault)
        private val LOCALE_REFERENCES = listOf(
            "setDefault",
        )

        // Play Core SplitInstallManager methods for downloading language splits
        private val PLAY_CORE_METHODS = listOf(
            "startInstall",
            "deferredInstall",
            "requestInstall",
        )

        // Play Core classes that handle language splits
        private const val SPLIT_INSTALL_MANAGER = "com.google.android.play.core.splitinstall.SplitInstallManager"
        private const val SPLIT_INSTALL_REQUEST = "com.google.android.play.core.splitinstall.SplitInstallRequest"
        private const val SPLIT_INSTALL_REQUEST_BUILDER = "com.google.android.play.core.splitinstall.SplitInstallRequest\$Builder"

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation =
                """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                If the app uses App Bundles and changes the locale at runtime without either \
                disabling locale splits or using the Play Core library to download the necessary \
                locale resources, the app may crash or display incorrect resources.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes \
                for more details.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
                Scope.JAVA_FILE_SCOPE,
                Scope.GRADLE_SCOPE,
            ),
            androidSpecific = true,
        )
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    override fun getApplicableReferenceNames(): List<String> = LOCALE_REFERENCES

    override fun getApplicableMethodNames(): List<String> = LOCALE_METHODS + PLAY_CORE_METHODS

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement,
    ) {
        // Check for Locale.setDefault(locale) calls via reference
        val name = reference.resolvedName ?: return
        if (name == "setDefault") {
            val evaluator = context.evaluator
            val psiClass = (referenced as? PsiMethod)?.containingClass ?: return
            if (evaluator.inheritsFrom(psiClass, "java.util.Locale", false)) {
                recordLocaleChange(context, context.getLocation(reference))
            }
        }
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val evaluator = context.evaluator

        when {
            methodName in PLAY_CORE_METHODS -> {
                // Check if this is a Play Core SplitInstallManager call involving languages
                val containingClass = method.containingClass ?: return
                if (evaluator.inheritsFrom(containingClass, SPLIT_INSTALL_MANAGER, true) ||
                    evaluator.inheritsFrom(containingClass, SPLIT_INSTALL_REQUEST_BUILDER, true)
                ) {
                    recordPlayCoreUsage(context, context.getLocation(node))
                }
            }

            methodName == "setLocale" || methodName == "setLocales" -> {
                // Check for ContextWrapper/Configuration/LocaleList setLocale calls
                val containingClass = method.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName ?: return
                if (qualifiedName == "android.content.res.Configuration" ||
                    qualifiedName == "android.os.LocaleList" ||
                    evaluator.inheritsFrom(containingClass, "android.content.ContextWrapper", false)
                ) {
                    recordLocaleChange(context, context.getLocation(node))
                }
            }

            methodName == "setDefaultLocale" -> {
                recordLocaleChange(context, context.getLocation(node))
            }

            methodName == "applyOverrideConfiguration" -> {
                val containingClass = method.containingClass ?: return
                if (evaluator.inheritsFrom(containingClass, "android.content.ContextWrapper", false)) {
                    recordLocaleChange(context, context.getLocation(node))
                }
            }

            methodName == "updateConfiguration" || methodName == "updateLocale" -> {
                val containingClass = method.containingClass ?: return
                if (evaluator.inheritsFrom(containingClass, "android.content.res.Resources", false) ||
                    evaluator.inheritsFrom(containingClass, "android.content.Context", false)
                ) {
                    recordLocaleChange(context, context.getLocation(node))
                }
            }
        }
    }

    private fun recordLocaleChange(context: JavaContext, location: Location) {
        val map = context.getPartialResults(ISSUE).map()
        // Store that we found a locale change; use a counter key to allow multiple entries
        val count = map.getInt(KEY_LOCALE_CHANGE, 0)
        map.put(KEY_LOCALE_CHANGE, count + 1)
        // Store the first location for reporting
        if (count == 0) {
            map.put(KEY_LOCATION, location)
        }
    }

    private fun recordPlayCoreUsage(context: JavaContext, location: Location) {
        val map = context.getPartialResults(ISSUE).map()
        map.put(KEY_PLAY_CORE_USED, true)
    }

    // -------------------------------------------------------------------------
    // GradleScanner
    // -------------------------------------------------------------------------

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
        // Look for:
        //   bundle { language { enableSplit = false } }
        // or
        //   splits { language { enable false } }
        if (property == "enableSplit" && value == "false" && parent == "language") {
            val map = context.getPartialResults(ISSUE).map()
            map.put(KEY_SPLITS_LOCALE_DISABLED, true)
        }
        if (property == "enable" && value == "false" && parent == "language") {
            val map = context.getPartialResults(ISSUE).map()
            map.put(KEY_SPLITS_LOCALE_DISABLED, true)
        }
    }

    // -------------------------------------------------------------------------
    // afterCheckEachProject / checkPartialResults
    // -------------------------------------------------------------------------

    override fun afterCheckEachProject(context: Context) {
        // Nothing extra needed here; partial results are accumulated automatically.
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        // Merge results from all modules/files
        var hasLocaleChange = false
        var hasPlayCore = false
        var hasSplitsDisabled = false
        var firstLocation: Location? = null

        for ((_, map) in partialResults) {
            if (map.getInt(KEY_LOCALE_CHANGE, 0) > 0) {
                hasLocaleChange = true
                if (firstLocation == null) {
                    firstLocation = map.getLocation(KEY_LOCATION)
                }
            }
            if (map.getBoolean(KEY_PLAY_CORE_USED, false)) {
                hasPlayCore = true
            }
            if (map.getBoolean(KEY_SPLITS_LOCALE_DISABLED, false)) {
                hasSplitsDisabled = true
            }
        }

        if (hasLocaleChange && !hasPlayCore && !hasSplitsDisabled) {
            val location = firstLocation ?: context.project.dir.let {
                Location.create(it)
            }
            val message =
                "Found a locale change at runtime, but the App Bundle is not configured to " +
                    "handle this: either disable locale splits in the bundle configuration " +
                    "(`bundle { language { enableSplit = false } }`) or use the Play Core " +
                    "library to download the required locale resources at runtime. " +
                    "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
            context.report(
                Incident(
                    ISSUE,
                    location,
                    message,
                )
            )
        }
    }
}