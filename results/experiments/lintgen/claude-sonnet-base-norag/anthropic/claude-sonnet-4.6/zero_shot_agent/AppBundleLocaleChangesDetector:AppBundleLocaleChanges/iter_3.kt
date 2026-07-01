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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), GradleScanner, SourceCodeScanner {

    companion object {
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
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
                EnumSet.of(Scope.JAVA_FILE),
                EnumSet.of(Scope.GRADLE_FILE)
            ),
            moreInfo = "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
        )

        private val LOCALE_CHANGE_METHODS = setOf(
            "setLocale",
            "setLocales",
            "setDefaultLocale",
            "applyOverrideConfiguration",
            "updateConfiguration",
            "setLanguage"
        )
    }

    // Track whether the bundle language split is disabled
    private var bundleLanguageSplitDisabled = false

    // Track whether Play Core library is used
    private var playCoreUsed = false

    // Track locale change calls found in Java/Kotlin files
    private val localeChangeCalls = mutableListOf<Pair<JavaContext, UCallExpression>>()

    override fun getApplicableMethodNames(): List<String> = LOCALE_CHANGE_METHODS.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        localeChangeCalls.add(Pair(context, node))
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
        // Check for bundle { language { enableSplit = false } }
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            if (value == "false") {
                bundleLanguageSplitDisabled = true
            }
        }

        // Check for Play Core dependency in string form
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
        // Check dependency declarations
        if (parent == "dependencies") {
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

    private fun checkForPlayCoreDependency(value: String) {
        val cleaned = value.trim('"', '\'', ' ')
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

    override fun afterCheckEachProject(context: Context) {
        if (!bundleLanguageSplitDisabled && !playCoreUsed) {
            for ((javaContext, call) in localeChangeCalls) {
                javaContext.report(
                    ISSUE,
                    call,
                    javaContext.getLocation(call),
                    "Found dynamic locale changes in an app using App Bundles but the bundle is not " +
                            "configured to handle language splits: either disable language splits in the " +
                            "bundle configuration or use the Play Core library to download locales at runtime. " +
                            "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"
                )
            }
        }

        // Reset state
        bundleLanguageSplitDisabled = false
        playCoreUsed = false
        localeChangeCalls.clear()
    }
}