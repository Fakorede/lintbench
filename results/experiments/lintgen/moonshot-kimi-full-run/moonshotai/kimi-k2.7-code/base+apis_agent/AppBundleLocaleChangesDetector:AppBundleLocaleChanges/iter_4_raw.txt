package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.GradleScanner
import com.android.tools.lint.detector.api.FileType
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

private fun String.trimQuotes(): String {
    return removeSurrounding("\"").removeSurending("'")
}

class AppBundleLocaleChangesDetector : Detector(), GradleScanner {

    private var isApplication = false
    private var hasPlayCore = false
    private var languageSplitDisabled = false

    override fun getApplicableFiles(): List<FileType> = listOf(FileType.GRADLE)

    override fun getApplicableMethodCalls(): List<String>? = listOf(
        "apply",
        "id",
        "enableSplit",
        "implementation",
        "api",
        "compile",
        "compileOnly",
        "runtimeOnly",
        "testImplementation",
        "androidTestImplementation"
    )

    override fun getApplicableDslProperties(): List<String>? = listOf(
        "enableSplit",
        "isEnableSplit"
    )

    override fun beforeCheckFile(context: Context) {
        isApplication = false
        hasPlayCore = false
        languageSplitDisabled = false
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
        when {
            statement == "apply" && namedArguments["plugin"]?.trimQuotes() == "com.android.application" -> {
                isApplication = true
            }
            statement == "id" && parent == "plugins" && unnamedArguments.any { it.trimQuotes() == "com.android.application" } -> {
                isApplication = true
            }
            parent == "dependencies" && unnamedArguments.any { PLAY_CORE_REGEX.containsMatchIn(it.trimQuotes()) } -> {
                hasPlayCore = true
            }
            statement == "enableSplit"
                && parent == "language"
                && parentParent == "bundle"
                && unnamedArguments.any { it.trimQuotes() == "false" } -> {
                languageSplitDisabled = true
            }
        }
    }

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
        if ((property == "enableSplit" || property == "isEnableSplit")
            && parent == "language"
            && parentParent == "bundle"
            && value.trimQuotes() == "false"
        ) {
            languageSplitDisabled = true
        }
    }

    override fun afterCheckFile(context: Context) {
        if (isApplication && !languageSplitDisabled && !hasPlayCore) {
            val contents = context.getContents()?.toString() ?: ""
            val location = Location.create(context.file, contents, 0, 0)
            context.report(
                ISSUE,
                location,
                "App Bundle language splits are enabled by default. When changing locales at runtime, " +
                    "either disable language splits in the bundle configuration " +
                    "(bundle { language { enableSplit = false } }) or use the Play Core library " +
                    "to download additional language splits."
            )
        }
    }

    companion object {
        private val PLAY_CORE_REGEX = Regex("""com\.google\.android\.play:core""")

        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle language changes at runtime",
            explanation = """
                When changing locales at runtime (for example, with an in-app language switcher), the
                Android App Bundle must be configured to not split by locale, or the Play Core library
                must be used to download additional language splits at runtime.

                Otherwise, the app may not have the resources for the new locale after the change.
            """.trimIndent(),
            category = Category.I18N,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.GRADLE_SCOPE
            )
        )
    }
}