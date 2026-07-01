package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"
        private const val SOFTWARE_LEANBACK = "android.software.leanback"
        private const val PERMISSION_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        private const val PERMISSION_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERMISSION_ACCESS_WIFI_STATE = "android.permission.ACCESS_WIFI_STATE"

        private val WIFI_PERMISSIONS = setOf(
            PERMISSION_CHANGE_WIFI_MULTICAST_STATE,
            PERMISSION_CHANGE_WIFI_STATE,
            PERMISSION_ACCESS_WIFI_STATE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using `android.hardware.wifi` on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet \
                via alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes \
                to connect to the internet, please modify your Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val MESSAGE = "Requiring `android.hardware.wifi` is not recommended for " +
            "Android TV apps as many TV devices connect via Ethernet. Consider setting " +
            "`android:required=\"false\"` and detecting connectivity using " +
            "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` or " +
            "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`."
    }

    // Per-project state stored in the context's client data
    private data class ManifestState(
        var hasLeanback: Boolean = false,
        var wifiRequiredFalse: Boolean = false,
        val pendingReports: MutableList<Triple<Element, XmlContext, String>> = mutableListOf()
    )

    // Map from manifest file path to state, to handle multi-project scenarios
    private val projectStates = mutableMapOf<String, ManifestState>()

    private fun getState(context: XmlContext): ManifestState {
        val key = context.project.dir.absolutePath
        return projectStates.getOrPut(key) { ManifestState() }
    }

    override fun beforeCheckFile(context: Context) {
        if (context is XmlContext) {
            val key = context.project.dir.absolutePath
            projectStates[key] = ManifestState()
        }
    }

    override fun afterCheckFile(context: Context) {
        if (context is XmlContext) {
            val key = context.project.dir.absolutePath
            val state = projectStates[key] ?: return
            if (state.hasLeanback && !state.wifiRequiredFalse) {
                for ((element, xmlContext, message) in state.pendingReports) {
                    xmlContext.report(
                        issue = ISSUE,
                        scope = element,
                        location = xmlContext.getLocation(element),
                        message = message
                    )
                }
            }
            projectStates.remove(key)
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE, TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName ?: element.localName ?: return
        val state = getState(context)

        when (tagName) {
            TAG_USES_FEATURE -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                when {
                    name == SOFTWARE_LEANBACK -> {
                        state.hasLeanback = true
                    }
                    name == HARDWARE_WIFI -> {
                        val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                        if (requiredAttr != null && requiredAttr.value == "false") {
                            state.wifiRequiredFalse = true
                        } else {
                            state.pendingReports.add(Triple(element, context, MESSAGE))
                        }
                    }
                }
            }
            TAG_USES_PERMISSION -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name in WIFI_PERMISSIONS) {
                    state.pendingReports.add(Triple(element, context, MESSAGE))
                }
            }
        }
    }
}