package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf("uses-feature", "category")

    override fun beforeCheckRootProject(context: Context) {
        projectStates.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val state = projectStates.getOrPut(context.project) { ProjectState() }
        when (element.tagName) {
            "uses-feature" -> {
                val name = element.getAttributeNS(ANDROID_URI, "name")
                when (name) {
                    WIFI_FEATURE -> {
                        state.wifiElement = element
                        state.wifiLocation = context.getLocation(element)
                        val required = element.getAttributeNS(ANDROID_URI, "required")
                        state.wifiRequired = required.isBlank() ||
                                !required.equals("false", ignoreCase = true)
                    }
                    LEANBACK_FEATURE -> state.isTvApp = true
                }
            }
            "category" -> {
                if (element.getAttributeNS(ANDROID_URI, "name") == LEANBACK_LAUNCHER_CATEGORY) {
                    state.isTvApp = true
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val state = projectStates[context.project] ?: return
        if (state.isTvApp && state.wifiRequired && state.wifiElement != null && state.wifiLocation != null) {
            val fix = LintFix.create()
                .set(ANDROID_URI, "required", "false")
                .build()
            context.report(
                ISSUE,
                state.wifiElement,
                state.wifiLocation,
                "Using `android.hardware.wifi` on TV; declare it with `android:required=\"false\"`",
                fix
            )
        }
    }

    private class ProjectState(
        var isTvApp: Boolean = false,
        var wifiElement: Element? = null,
        var wifiLocation: Location? = null,
        var wifiRequired: Boolean = true
    )

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val WIFI_FEATURE = "android.hardware.wifi"
        private const val LEANBACK_FEATURE = "android.software.leanback"
        private const val LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER"

        private val projectStates = mutableMapOf<Project, ProjectState>()

        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.MANIFEST_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }
}