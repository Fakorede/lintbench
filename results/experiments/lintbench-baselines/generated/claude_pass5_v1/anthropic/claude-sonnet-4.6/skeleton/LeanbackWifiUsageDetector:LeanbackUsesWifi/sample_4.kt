package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via \
                alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to \
                connect to the internet, please modify your Manifest to contain: \
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val USES_FEATURE_TAG = "uses-feature"
        private const val USES_PERMISSION_TAG = "uses-permission"
        private const val HARDWARE_WIFI = "android.hardware.wifi"
        private const val PERMISSION_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERMISSION_ACCESS_WIFI_STATE = "android.permission.ACCESS_WIFI_STATE"
        private const val ATTR_NAME = "name"
        private const val ATTR_REQUIRED = "required"
        private const val LEANBACK_FEATURE = "android.software.leanback"
    }

    /**
     * Tracks whether the merged manifest declares the leanback feature.
     */
    private var hasLeanbackFeature = false

    /**
     * Tracks whether wifi is declared as required=false in the merged manifest.
     */
    private var wifiNotRequired = false

    /**
     * Tracks whether wifi is declared as required=true (or without required attr) in the merged manifest.
     */
    private var wifiRequired = false

    /**
     * Tracks whether any wifi-related permission is declared.
     */
    private var hasWifiPermission = false

    /**
     * Location of the wifi uses-feature element (if required).
     */
    private var wifiFeatureLocation: Location? = null

    override fun checkMergedProject(context: Context) {
        // Reset state before checking
        hasLeanbackFeature = false
        wifiNotRequired = false
        wifiRequired = false
        hasWifiPermission = false
        wifiFeatureLocation = null

        val mergedManifest = context.mainProject.mergedManifest ?: return
        val documentElement = mergedManifest.documentElement ?: return

        val children = documentElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            when (node.tagName) {
                USES_FEATURE_TAG -> {
                    val name = node.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME)
                    when (name) {
                        LEANBACK_FEATURE -> {
                            hasLeanbackFeature = true
                        }
                        HARDWARE_WIFI -> {
                            val requiredAttr = node.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_REQUIRED)
                            if (requiredAttr == "false") {
                                wifiNotRequired = true
                            } else {
                                wifiRequired = true
                                wifiFeatureLocation = context.getLocation(node)
                            }
                        }
                    }
                }
                USES_PERMISSION_TAG -> {
                    val name = node.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME)
                    if (name == PERMISSION_CHANGE_WIFI_STATE || name == PERMISSION_ACCESS_WIFI_STATE) {
                        hasWifiPermission = true
                    }
                }
            }
        }

        // Only report if this is a leanback (TV) app
        if (!hasLeanbackFeature) return

        // If wifi is explicitly marked as not required, no issue
        if (wifiNotRequired) return

        // Report if wifi is explicitly required or if wifi permissions are used without required=false
        if (wifiRequired) {
            val location = wifiFeatureLocation ?: context.getLocation(documentElement)
            context.report(
                issue = ISSUE,
                location = location,
                message = "Wifi is not required for Android TV and many devices connect to the " +
                    "internet via alternative methods e.g. Ethernet. If your app is not " +
                    "focused specifically on WiFi functionality, please set " +
                    "`android:required=\"false\"` on the `android.hardware.wifi` " +
                    "`uses-feature` declaration."
            )
        } else if (hasWifiPermission) {
            val location = context.getLocation(documentElement)
            context.report(
                issue = ISSUE,
                location = location,
                message = "Wifi is not required for Android TV and many devices connect to the " +
                    "internet via alternative methods e.g. Ethernet. If your app is not " +
                    "focused specifically on WiFi functionality, please add " +
                    "`<uses-feature android:name=\"android.hardware.wifi\" " +
                    "android:required=\"false\" />` to your manifest."
            )
        }
    }
}