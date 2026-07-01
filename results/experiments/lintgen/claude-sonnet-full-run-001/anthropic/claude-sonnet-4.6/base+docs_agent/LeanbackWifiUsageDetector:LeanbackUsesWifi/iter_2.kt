package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE, TAG_USES_PERMISSION)
    }

    private fun hasLeanbackFeature(element: Element): Boolean {
        val document = element.ownerDocument
        val usesFeatures = document.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until usesFeatures.length) {
            val feature = usesFeatures.item(i) as? Element ?: continue
            val featureName = feature.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (featureName == SOFTWARE_LEANBACK) {
                return true
            }
        }
        return false
    }

    private fun hasWifiRequiredFalse(element: Element): Boolean {
        val document = element.ownerDocument
        val usesFeatures = document.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until usesFeatures.length) {
            val feature = usesFeatures.item(i) as? Element ?: continue
            val featureName = feature.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (featureName == HARDWARE_WIFI) {
                val requiredAttr = feature.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                if (requiredAttr != null && requiredAttr.value == "false") {
                    return true
                }
            }
        }
        return false
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName ?: element.localName ?: return

        when (tagName) {
            TAG_USES_FEATURE -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name != HARDWARE_WIFI) return

                val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                if (requiredAttr != null && requiredAttr.value == "false") return

                if (!hasLeanbackFeature(element)) return

                context.report(
                    issue = ISSUE,
                    scope = element,
                    location = context.getLocation(element),
                    message = MESSAGE
                )
            }
            TAG_USES_PERMISSION -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name !in WIFI_PERMISSIONS) return

                if (!hasLeanbackFeature(element)) return

                if (hasWifiRequiredFalse(element)) return

                context.report(
                    issue = ISSUE,
                    scope = element,
                    location = context.getLocation(element),
                    message = MESSAGE
                )
            }
        }
    }
}