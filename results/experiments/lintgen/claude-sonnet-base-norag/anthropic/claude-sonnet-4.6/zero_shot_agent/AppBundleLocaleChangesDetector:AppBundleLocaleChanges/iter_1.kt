package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), GradleScanner {

    companion object {
        private const val PLAY_CORE_GROUP = "com.google.android.play"
        private const val PLAY_CORE_ARTIFACT = "core"
        private const val PLAY_CORE_KTX_ARTIFACT = "core-ktx"
        private const val PLAY_FEATURE_DELIVERY_ARTIFACT = "feature-delivery"
        private const val PLAY_FEATURE_DELIVERY_KTX_ARTIFACT = "feature-delivery-ktx"

        // Also check for the older com.google.android.play:core artifact
        private const val PLAY_CORE_OLD_GROUP = "com.google.android.play"

        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.GRADLE_FILE)
            ),
            moreInfo = "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
        )
    }

    // Track whether the bundle language split is disabled
    private var bundleLanguageSplitDisabled = false

    // Track whether Play Core library is used
    private var playCoreUsed = false

    // Track location of the android block for reporting
    private var androidBlockLocation: Location? = null

    // State tracking for nested blocks
    private var inAndroidBlock = false
    private var inBundleBlock = false
    private var inLanguageBlock = false

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
        // Check for bundle { language { enableSplit = false } }
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            if (value == "false") {
                bundleLanguageSplitDisabled = true
            }
        }

        // Check for Play Core dependency
        if (parent == "dependencies") {
            checkForPlayCoreDependency(value)
        }
    }

    override fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        parentParent: String?,
        namedArguments: Map<String, String>,
        unnamedArguments: List<String>,
        cookie: Any
    ) {
        // Check dependency declarations like implementation("com.google.android.play:core:x.y.z")
        if (isInDependenciesBlock(parent, parentParent)) {
            for (arg in unnamedArguments) {
                checkForPlayCoreDependency(arg)
            }
            val group = namedArguments["group"]
            val name = namedArguments["name"]
            if (group != null && name != null) {
                checkForPlayCoreGroupAndName(group, name)
            }
        }
    }

    private fun isInDependenciesBlock(parent: String?, parentParent: String?): Boolean {
        return parent == "dependencies" ||
                parentParent == "dependencies"
    }

    private fun checkForPlayCoreDependency(value: String) {
        val cleaned = value.trim('"', '\'', ' ')
        // Check for com.google.android.play:core, core-ktx, feature-delivery, feature-delivery-ktx
        if (cleaned.startsWith("com.google.android.play:core") ||
            cleaned.startsWith("com.google.android.play:feature-delivery")
        ) {
            playCoreUsed = true
        }
    }

    private fun checkForPlayCoreGroupAndName(group: String, name: String) {
        val cleanGroup = group.trim('"', '\'', ' ')
        val cleanName = name.trim('"', '\'', ' ')
        if (cleanGroup == "com.google.android.play" &&
            (cleanName == "core" || cleanName == "core-ktx" ||
                    cleanName == "feature-delivery" || cleanName == "feature-delivery-ktx")
        ) {
            playCoreUsed = true
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context !is GradleContext) return

        // Only report if neither condition is met
        if (!bundleLanguageSplitDisabled && !playCoreUsed) {
            // We need to check if this is an app module (has android block with applicationId or similar)
            // For simplicity, report on any build.gradle that has android block
            // The issue should be reported at the file level
            context.report(
                ISSUE,
                Location.create(context.file),
                "Found dynamic locale changes in an app using App Bundles but the bundle is not " +
                        "configured to handle language splits: either disable language splits in the " +
                        "bundle configuration or use the Play Core library to download locales at runtime. " +
                        "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
            )
        }

        // Reset state for next file
        bundleLanguageSplitDisabled = false
        playCoreUsed = false
        androidBlockLocation = null
    }
}