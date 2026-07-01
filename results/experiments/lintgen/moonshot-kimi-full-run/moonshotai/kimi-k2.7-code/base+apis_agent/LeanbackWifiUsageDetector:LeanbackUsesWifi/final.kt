package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String> =
        listOf("uses-feature", "uses-permission", "category")

    override fun beforeCheckRootProject(context: Context) {
        projectStates.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val state = projectStates.getOrPut(context.project) {
            ProjectState().apply { this.context = context }
        }
        if (state.context == null) {
            state.context = context
        }

        when (element.tagName) {
            "uses-feature" -> {
                val name = element.getAttributeNS(ANDROID_URI, "name")
                when (name) {
                    WIFI_FEATURE -> {
                        val required = element.getAttributeNS(ANDROID_URI, "required")
                        if (required.equals("false", ignoreCase = true)) {
                            state.hasWifiFeatureOptional = true
                        } else {
                            state.wifiFeatureRequired = element to context.getLocation(element)
                        }
                    }
                    LEANBACK_FEATURE -> state.isTv = true
                }
            }
            "uses-permission" -> {
                val name = element.getAttributeNS(ANDROID_URI, "name")
                if (name in WIFI_PERMISSIONS) {
                    state.wifiPermissions.add(element to context.getLocation(element))
                }
            }
            "category" -> {
                if (element.getAttributeNS(ANDROID_URI, "name") == LEANBACK_LAUNCHER_CATEGORY) {
                    state.isTv = true
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val projects = LinkedHashSet<Project>()
        projects.add(context.project)
        projects.addAll(context.project.allLibraries)

        val isTv = projects.any { projectStates[it]?.isTv == true }
        if (!isTv) {
            projectStates.clear()
            return
        }

        for (project in projects) {
            val state = projectStates[project] ?: continue
            val target = state.wifiFeatureRequired ?: state.wifiPermissions.firstOrNull()
            if (target != null) {
                val (element, location) = target
                val fix = if (state.wifiFeatureRequired != null) {
                    LintFix.create().set(ANDROID_URI, "required", "false").build()
                } else null
                state.context?.report(ISSUE, element, location, MESSAGE, fix)
            }
        }

        projectStates.clear()
    }

    private class ProjectState(
        var isTv: Boolean = false,
        var hasWifiFeatureOptional: Boolean = false,
        var wifiFeatureRequired: Pair<Element, Location>? = null,
        val wifiPermissions: MutableList<Pair<Element, Location>> = mutableListOf(),
        var context: XmlContext? = null
    )

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val WIFI_FEATURE = "android.hardware.wifi"
        private const val LEANBACK_FEATURE = "android.software.leanback"
        private const val LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER"
        private const val MESSAGE = "Using `android.hardware.wifi` on TV; declare it with `android:required=\"false\"`"

        private val WIFI_PERMISSIONS = listOf(
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        )

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