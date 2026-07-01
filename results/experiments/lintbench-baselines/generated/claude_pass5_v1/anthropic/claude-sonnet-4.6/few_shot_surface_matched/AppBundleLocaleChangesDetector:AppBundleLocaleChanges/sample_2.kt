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
        private const val KEY_SPLITS_LOCALE_DISABLED = "splitsLocaleDisabled"
        private const val KEY_PLAY_CORE_USED = "playCoreUsed"

        private const val LOCALE_LIST_CLASS = "android.os.LocaleList"
        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val APP_COMPAT_DELEGATE_CLASS = "androidx.appcompat.app.AppCompatDelegate"
        private const val LOCALE_MANAGER_CLASS = "android.app.LocaleManager"

        private val LOCALE_METHOD_NAMES = listOf(
            "setLocales",
            "setApplicationLocales",
            "setDefault",
            "updateConfiguration",
            "applyOverrideConfiguration",
        )

        private val LOCALE_REFERENCE_NAMES = listOf(
            "setLocales",
            "setApplicationLocales",
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation =
                """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale, or the Play \
                Core library must be used to download additional locales at runtime.

                If you are using an App Bundle and changing the locale at runtime without \
                disabling locale splits or using the Play Core library, some resources may not \
                be available.

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
            ),
            androidSpecific = true,
        )

        private const val KEY_LOCATION_FILE = "file"
        private const val KEY_LOCATION_LINE = "line"
        private const val KEY_LOCATION_COLUMN = "column"
        private const val KEY_LOCATION_MESSAGE = "message"
        private const val SEPARATOR = "|"
    }

    // Track whether the current project has locale splits disabled in Gradle
    private var splitsLocaleDisabled = false

    // Track whether Play Core library is used
    private var playCoreUsed = false

    // -------------------------------------------------------------------------
    // SourceCodeScanner
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = LOCALE_METHOD_NAMES

    override fun getApplicableReferenceNames(): List<String> = LOCALE_REFERENCE_NAMES

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!isLocaleChangeMethod(context, method)) return
        recordLocaleChange(context, node)
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: com.intellij.psi.PsiElement,
    ) {
        // Additional reference-based detection (e.g. field references related to locale)
        val name = reference.resolvedName ?: return
        if (name !in LOCALE_REFERENCE_NAMES) return
        if (referenced !is PsiMethod) return
        if (!isLocaleChangeMethod(context, referenced)) return
        // visitMethodCall will cover the call site; this handles pure reference usage
        recordLocaleChange(context, reference)
    }

    private fun isLocaleChangeMethod(context: JavaContext, method: PsiMethod): Boolean {
        val evaluator = context.evaluator
        return evaluator.isMemberInSubClassOf(method, LOCALE_LIST_CLASS, false) ||
            evaluator.isMemberInSubClassOf(method, CONFIGURATION_CLASS, false) ||
            evaluator.isMemberInSubClassOf(method, APP_COMPAT_DELEGATE_CLASS, false) ||
            evaluator.isMemberInSubClassOf(method, LOCALE_MANAGER_CLASS, false)
    }

    private fun recordLocaleChange(context: JavaContext, node: org.jetbrains.uast.UElement) {
        val location = context.getLocation(node)
        val map = context.getPartialResults(ISSUE).map()
        storeLocation(map, location)
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
        // Detect: splits { language { enable false } }
        // or: bundle { language { enableSplit = false } }
        if (property == "enable" || property == "enableSplit") {
            if ((parent == "language" || parent == "abi" || parent == "density") &&
                parent == "language" &&
                value == "false"
            ) {
                val map = context.getPartialResults(ISSUE).map()
                map.put(KEY_SPLITS_LOCALE_DISABLED, true)
                splitsLocaleDisabled = true
            }
        }

        // Detect Play Core dependency
        if (property == "implementation" || property == "api" ||
            property == "compile" || property == "runtimeOnly"
        ) {
            if (value.contains("com.google.android.play:core") ||
                value.contains("com.google.android.play:app-update") ||
                value.contains("play-core") ||
                value.contains("com.google.android.play:feature-delivery")
            ) {
                val map = context.getPartialResults(ISSUE).map()
                map.put(KEY_PLAY_CORE_USED, true)
                playCoreUsed = true
            }
        }
    }

    // -------------------------------------------------------------------------
    // afterCheckEachProject / checkPartialResults
    // -------------------------------------------------------------------------

    override fun afterCheckEachProject(context: Context) {
        // Nothing to do here; reporting is deferred to checkPartialResults
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var hasLocaleChanges = false
        var localeChangeLocation: Location? = null
        var localeSplitsDisabled = false
        var coreUsed = false

        for ((_, map) in partialResults) {
            if (map.containsKey(KEY_SPLITS_LOCALE_DISABLED)) {
                localeSplitsDisabled = true
            }
            if (map.containsKey(KEY_PLAY_CORE_USED)) {
                coreUsed = true
            }
            // Check for stored locale change locations
            val locationData = map.getString(KEY_LOCALE_CHANGE_LOCATIONS, null)
            if (!locationData.isNullOrEmpty()) {
                hasLocaleChanges = true
                if (localeChangeLocation == null) {
                    localeChangeLocation = restoreLocation(context, locationData)
                }
            }
        }

        if (hasLocaleChanges && !localeSplitsDisabled && !coreUsed) {
            val location = localeChangeLocation ?: Location.create(context.project.dir)
            context.report(
                Incident(
                    ISSUE,
                    location,
                    "Found runtime locale changes, but the app bundle is not configured to " +
                        "handle this. Either disable locale splits in your Gradle configuration " +
                        "(`bundle { language { enableSplit = false } }`) or use the Play Core " +
                        "library to download additional locales at runtime. " +
                        "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes",
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers to serialize/deserialize Location into LintMap
    // -------------------------------------------------------------------------

    private fun storeLocation(map: LintMap, location: Location) {
        val file = location.file.path
        val line = location.start?.line ?: 0
        val column = location.start?.column ?: 0
        // Append to existing entries (store only the first occurrence for simplicity)
        if (!map.containsKey(KEY_LOCALE_CHANGE_LOCATIONS)) {
            map.put(KEY_LOCALE_CHANGE_LOCATIONS, "$file$SEPARATOR$line$SEPARATOR$column")
        }
    }

    private fun restoreLocation(context: Context, data: String): Location? {
        return try {
            val parts = data.split(SEPARATOR)
            if (parts.size < 3) return null
            val file = java.io.File(parts[0])
            val line = parts[1].toIntOrNull() ?: 0
            val column = parts[2].toIntOrNull() ?: 0
            Location.create(file, null, line)
        } catch (e: Exception) {
            null
        }
    }
}