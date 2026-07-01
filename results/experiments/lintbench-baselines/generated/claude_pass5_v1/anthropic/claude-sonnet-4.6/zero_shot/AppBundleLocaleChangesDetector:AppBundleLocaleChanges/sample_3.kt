package com.android.tools.lint.checks

import com.android.SdkConstants
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

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    // Tracks whether the project uses Play Core's SplitInstallManager or
    // disables language splits in the bundle configuration.
    private var bundleLanguageSplitDisabled = false
    private var playCoreLocaleRequestFound = false

    // Locations of locale-change calls found in Java/Kotlin source so we can
    // report them once we know the project configuration.
    private val localeChangeCalls = mutableListOf<Pair<JavaContext, Location>>()

    // -----------------------------------------------------------------------
    // GradleScanner – look for bundle { language { enableSplit = false } }
    // -----------------------------------------------------------------------

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
        // We are looking for:
        //   android {
        //     bundle {
        //       language {
        //         enableSplit = false
        //       }
        //     }
        //   }
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            if (value == SdkConstants.VALUE_FALSE || value == "false") {
                bundleLanguageSplitDisabled = true
            }
        }
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner – look for locale / configuration changes and Play Core
    // -----------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = listOf(
        // AppCompatDelegate / AppCompat in-app language APIs
        "setApplicationLocales",
        "setDefaultLocale",
        // Configuration-based locale change
        "updateConfiguration",
        "applyOverrideConfiguration",
        "createConfigurationContext",
        // Play Core SplitInstallManager language request
        "requestInstall",
        "deferredInstall",
        // Locale helpers
        "setLocale",
        "setLanguage"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val methodName = method.name
        val containingClass = method.containingClass?.qualifiedName ?: ""

        // ---- Play Core usage – this is the correct pattern ----
        if (containingClass == "com.google.android.play.core.splitinstall.SplitInstallManager" ||
            containingClass == "com.google.android.play.core.splitinstall.SplitInstallRequest" ||
            containingClass == "com.google.android.play.core.splitinstall.SplitInstallRequest.Builder"
        ) {
            if (methodName == "requestInstall" || methodName == "deferredInstall") {
                playCoreLocaleRequestFound = true
                return
            }
        }

        // ---- Locale-change APIs that could be problematic ----
        val isLocaleChange = when {
            // AppCompatDelegate.setApplicationLocales (AndroidX)
            methodName == "setApplicationLocales" &&
                    (containingClass.contains("AppCompatDelegate") ||
                            containingClass.contains("LocaleManagerCompat")) -> true

            // Configuration-based approaches
            methodName == "updateConfiguration" &&
                    containingClass == "android.app.ActivityManager" -> true

            methodName == "updateConfiguration" &&
                    containingClass == "android.content.res.Resources" -> true

            methodName == "applyOverrideConfiguration" &&
                    containingClass.contains("ContextThemeWrapper") -> true

            methodName == "createConfigurationContext" &&
                    (containingClass == "android.content.Context" ||
                            containingClass.contains("ContextWrapper")) -> true

            // Generic setLocale / setLanguage on Locale or Configuration
            methodName == "setLocale" &&
                    (containingClass == "android.content.res.Configuration" ||
                            containingClass == "java.util.Locale" ||
                            containingClass.contains("LocaleList")) -> true

            methodName == "setDefaultLocale" &&
                    containingClass == "java.util.Locale" -> true

            methodName == "setDefault" &&
                    containingClass == "java.util.Locale" -> true

            else -> false
        }

        if (isLocaleChange) {
            localeChangeCalls.add(Pair(context, context.getLocation(node)))
        }
    }

    // We also want to catch Locale.setDefault(...)
    override fun getApplicableConstructorTypes(): List<String>? = null

    // -----------------------------------------------------------------------
    // afterCheckEachProject – report issues when we have full picture
    // -----------------------------------------------------------------------

    override fun afterCheckEachProject(context: Context) {
        if (localeChangeCalls.isEmpty()) {
            // No locale-change calls found – nothing to report.
            return
        }

        // If the developer has correctly disabled language splits OR is using
        // Play Core to fetch languages dynamically, we don't need to warn.
        if (bundleLanguageSplitDisabled || playCoreLocaleRequestFound) {
            return
        }

        for ((javaContext, location) in localeChangeCalls) {
            javaContext.report(
                issue = ISSUE,
                location = location,
                message = MESSAGE
            )
        }
    }

    // -----------------------------------------------------------------------
    // Companion
    // -----------------------------------------------------------------------

    companion object {

        private const val MESSAGE =
            "Found a runtime locale change, but the app is not handling App Bundle " +
                "language splits correctly. Either configure the bundle to disable " +
                "language splits (`bundle { language { enableSplit = false } }`) or " +
                "use the Play Core library to download language splits on demand. " +
                "See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language \
                switcher), the Android App Bundle must be configured to not split by \
                locale or the Play Core library must be used to download additional \
                locales at runtime.

                If the app bundle splits by language (the default) and the app changes \
                the locale at runtime without using Play Core's `SplitInstallManager`, \
                users may see missing strings or crashes because the required locale \
                resources were not packaged into the installed APK split.

                To fix this either:
                1. Disable language splits in your `build.gradle`:
                ```groovy
                android {
                    bundle {
                        language {
                            enableSplit = false
                        }
                    }
                }
                ```
                2. Or use the Play Core library's `SplitInstallManager` to request \
                   the required language splits at runtime before changing the locale.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
                EnumSet.of(Scope.JAVA_FILE),
                EnumSet.of(Scope.GRADLE_FILE)
            )
        )
    }
}