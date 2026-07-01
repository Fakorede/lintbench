package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var runtimeLocaleChangeFound = false
    private var localeChangeLocation: Location? = null
    private var languageSplitEnabled = true
    private var buildFileLocation: Location? = null

    override fun beforeCheckEachProject(context: Context) {
        runtimeLocaleChangeFound = false
        localeChangeLocation = null
        languageSplitEnabled = true
        buildFileLocation = null
    }

    override fun getApplicableCallNames(): List<String> = listOf(
        "setApplicationLocales",
        "setOverrideLocaleConfig"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        val methodName = node.methodName ?: return
        if (methodName == "setApplicationLocales" || methodName == "setOverrideLocaleConfig") {
            runtimeLocaleChangeFound = true
            if (localeChangeLocation == null) {
                localeChangeLocation = context.getLocation(node)
            }
        }
    }

    override fun getApplicableFiles(): List<String> = listOf(
        "build.gradle",
        "build.gradle.kts"
    )

    override fun visitBuildStatement(context: GradleContext, statement: String, value: Any?) {
        if (statement == "android.bundle.language.enableSplit" ||
            statement == "bundle.language.enableSplit"
        ) {
            languageSplitEnabled = value != false
            buildFileLocation = context.getLocation(context.file)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (!context.project.isAppProject) {
            return
        }

        if (runtimeLocaleChangeFound && languageSplitEnabled) {
            val location = localeChangeLocation
                ?: buildFileLocation
                ?: Location.create(context.file)

            val message = "App Bundle is configured to split by locale, but the app changes " +
                    "locales at runtime. Set `android.bundle.language.enableSplit` to false or " +
                    "use the Play Core library to download additional language splits."

            context.report(ISSUE, location, message)
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (for example to provide an in-app language
                switcher), the Android App Bundle must be configured to not split by locale or
                the Play Core library must be used to download additional language splits at
                runtime. Otherwise the resources for the newly selected locale may not be
                available.

                Reference: https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.GRADLE_SCOPE
            )
        )
    }
}