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
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

private const val ATTR_LOCALE_CONFIG = "localeConfig"
private const val REPORT_MESSAGE =
    "When changing locales at runtime, the App Bundle must be configured to not split by locale, or the Play Core library must be used to download additional locales at runtime."

class AppBundleLocaleChangesDetector : Detector(), XmlScanner, GradleScanner {

    private var hasRuntimeLocaleChange = false
    private var localeConfigLocation: Location? = null
    private var languageSplitDisabled = false
    private var hasPlayCoreLibrary = false

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_APPLICATION)

    override fun visitElement(context: XmlContext, element: Element) {
        val localeConfig = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_LOCALE_CONFIG)
        if (localeConfig.isNotBlank()) {
            hasRuntimeLocaleChange = true
            localeConfigLocation = context.getElementLocation(element)
        }
    }

    override fun visitBuildScript(context: GradleContext) {
        val source = try {
            context.file.readText()
        } catch (e: Exception) {
            return
        }

        if (BUNDLE_LANGUAGE_SPLIT_DISABLE_REGEX.containsMatchIn(source)) {
            languageSplitDisabled = true
        }

        if (PLAY_CORE_REGEX.containsMatchIn(source)) {
            hasPlayCoreLibrary = true
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!hasRuntimeLocaleChange || languageSplitDisabled || hasPlayCoreLibrary) {
            return
        }

        localeConfigLocation?.let { location ->
            context.report(
                ISSUE,
                location,
                REPORT_MESSAGE,
                null
            )
        }
    }

    companion object {
        private val BUNDLE_LANGUAGE_SPLIT_DISABLE_REGEX = Regex(
            """bundle\s*\{[\s\S]*?language\s*\{[\s\S]*?enableSplit\s*(?:=|:|\s)\s*false\b""",
            RegexOption.IGNORE_CASE
        )

        private val PLAY_CORE_REGEX = Regex(
            """com\.google\.android\.play:[\w.-]+""",
            RegexOption.IGNORE_CASE
        )

        private const val ID = "AppBundleLocaleChanges"
        private const val EXPLANATION = """
            When an app changes the active locale at runtime (for example, to provide an in-app
            language switcher), the Play Store's App Bundle language splitting can prevent the
            newly selected locale's resources from being available.

            You should either disable App Bundle language splitting in your build.gradle file:

                bundle {
                    language {
                        enableSplit = false
                    }
                }

            or use the Play Core library to download additional language splits at runtime.

            See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
        """

        @JvmField
        val ISSUE = Issue.create(
            ID,
            "App Bundle may not handle runtime locale changes",
            EXPLANATION,
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.MANIFEST_SCOPE,
                Scope.GRADLE_SCOPE
            )
        )
    }
}