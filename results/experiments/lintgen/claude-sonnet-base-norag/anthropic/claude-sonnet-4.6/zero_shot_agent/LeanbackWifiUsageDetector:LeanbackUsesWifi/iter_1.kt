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
        private const val PERMISSION_ACCESS_WIFI_STATE = "android.permission.ACCESS_WIFI_STATE"
        private const val PERMISSION_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERMISSION_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"

        private val WIFI_PERMISSIONS = setOf(
            PERMISSION_ACCESS_WIFI_STATE,
            PERMISSION_CHANGE_WIFI_STATE,
            PERMISSION_CHANGE_WIFI_MULTICAST_STATE
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
    }

    private var wifiRequiredFalse = false
    private var wifiRequiredElement: Element? = null
    private var wifiRequiredContext: XmlContext? = null
    private val wifiPermissionElements = mutableListOf<Pair<XmlContext, Element>>()

    override fun beforeCheckFile(context: Context) {
        wifiRequiredFalse = false
        wifiRequiredElement = null
        wifiRequiredContext = null
        wifiPermissionElements.clear()
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE, TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)

        when (tagName) {
            TAG_USES_FEATURE -> {
                if (name == HARDWARE_WIFI) {
                    val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                    if (required.equals("false", ignoreCase = true)) {
                        wifiRequiredFalse = true
                    } else {
                        wifiRequiredElement = element
                        wifiRequiredContext = context
                    }
                }
            }
            TAG_USES_PERMISSION -> {
                if (name in WIFI_PERMISSIONS) {
                    wifiPermissionElements.add(Pair(context, element))
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (wifiRequiredFalse) {
            return
        }

        val message = "Requiring `android.hardware.wifi` is not recommended for Android TV " +
            "apps; many TV devices connect via Ethernet or other means. Consider " +
            "adding `<uses-feature android:name=\"android.hardware.wifi\" " +
            "android:required=\"false\" />` if WiFi is not essential to your app."

        // Report on explicit uses-feature with required=true (or default)
        val featureElement = wifiRequiredElement
        val featureContext = wifiRequiredContext
        if (featureElement != null && featureContext != null) {
            featureContext.report(
                issue = ISSUE,
                location = featureContext.getElementLocation(featureElement),
                message = message
            )
        }

        // Report on WiFi permission usages
        for ((permContext, permElement) in wifiPermissionElements) {
            permContext.report(
                issue = ISSUE,
                location = permContext.getElementLocation(permElement),
                message = message
            )
        }
    }
}