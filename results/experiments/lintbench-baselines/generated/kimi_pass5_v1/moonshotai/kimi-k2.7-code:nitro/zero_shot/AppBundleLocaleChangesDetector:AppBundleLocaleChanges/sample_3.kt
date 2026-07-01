package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(),
    Detector.SourceCodeScanner,
    Detector.GradleScanner {

    private var hasRuntimeLocaleChange = false
    private var localeChangeLocation: Location? = null
    private var languageSplitDisabled = false
    private var playCorePresent = false

    override fun getApplicableCallNames(): List<String> = listOf("setApplicationLocales")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression) {
        if (node.methodName == "setApplicationLocales") {
            hasRuntimeLocaleChange = true
            if (localeChangeLocation == null) {
                localeChangeLocation = context.getLocation(node)
            }
        }
    }

    override fun getApplicableFiles(): List<Scope> = listOf(Scope.GRADLE_FILE)

    override fun visitBuildScript(context: GradleContext) {
        if (context.project?.isModule != true) return

        val source = context.file.readText()
        if (isLanguageSplitDisabled(source)) languageSplitDisabled = true
        if (isPlayCorePresent(source)) playCorePresent = true
    }

    override fun beforeCheckProject(context: Context) {
        hasRuntimeLocaleChange = false
        localeChangeLocation = null
        languageSplitDisabled = false
        playCorePresent = false
    }

    override fun afterCheckProject(context: Context) {
        if (hasRuntimeLocaleChange && !languageSplitDisabled && !playCorePresent) {
            val location = localeChangeLocation ?: return
            context.report(ISSUE, location, MESSAGE)
        }
    }

    private fun isLanguageSplitDisabled(source: String): Boolean {
        val regex = Regex(
            """language\s*\{[^{}]*enableSplit\s*[=:(]?\s*false[^{}]*\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        return regex.containsMatchIn(source)
    }

    private fun isPlayCorePresent(source: String): Boolean {
        return PLAY_CORE_DEPENDENCIES.any { source.contains(it) }
    }

    companion object {
        private const val MESSAGE =
            "When changing locales at runtime, the Android App Bundle must not split by " +
                    "locale or the Play Core library must be used to download additional " +
                    "language splits. Either set `bundle.language.enableSplit` to `false` or " +
                    "add a Play Core dependency."

        private val PLAY_CORE_DEPENDENCIES = listOf(
            "com.google.android.play:core",
            "com.google.android.play:core-ktx",
            "com.google.android.gms:play-services-splitinstall"
        )

        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle locale changes",
            explanation = """
                When changing locales at runtime (e.g., to provide an in-app language switcher),
                the Android App Bundle must be configured to not split by locale or the Play
                Core library must be used to download additional locales at runtime.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            )
        )
    }
}