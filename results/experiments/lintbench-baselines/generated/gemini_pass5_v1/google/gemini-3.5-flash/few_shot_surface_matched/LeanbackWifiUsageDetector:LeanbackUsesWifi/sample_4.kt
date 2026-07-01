package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.project.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        var usesLeanback = false
        var hasTvLauncher = false

        val usesFeatures = root.getElementsByTagName("uses-feature")
        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotEmpty() }
                ?: element.getAttribute("android:name")
            if (name == "android.software.leanback") {
                usesLeanback = true
                break
            }
        }

        if (!usesLeanback) {
            val categories = root.getElementsByTagName("category")
            for (i in 0 until categories.length) {
                val element = categories.item(i) as? org.w3c.dom.Element ?: continue
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("android:name")
                if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                    hasTvLauncher = true
                    break
                }
            }
        }

        val isTvApp = usesLeanback || hasTvLauncher
        if (!isTvApp) return

        var wifiFeature: org.w3c.dom.Element? = null
        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotEmpty() }
                ?: element.getAttribute("android:name")
            if (name == "android.hardware.wifi") {
                wifiFeature = element
                break
            }
        }

        var usesWifiPermission: org.w3c.dom.Element? = null
        val usesPermissions = root.getElementsByTagName("uses-permission")
        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME).takeIf { it.isNotEmpty() }
                ?: element.getAttribute("android:name")
            if (name == "android.permission.ACCESS_WIFI_STATE" ||
                name == "android.permission.CHANGE_WIFI_STATE") {
                usesWifiPermission = element
                break
            }
        }

        if (wifiFeature != null) {
            val requiredAttr = wifiFeature.getAttributeNodeNS(ANDROID_URI, "required")
                ?: wifiFeature.getAttributeNode("android:required")
            val isRequired = requiredAttr == null || requiredAttr.value != "false"
            if (isRequired) {
                val location = context.client.xmlParser.getLocation(wifiFeature)
                context.report(
                    ISSUE,
                    location,
                    "Looking for `android.hardware.wifi` but TV devices often use an Ethernet connection. " +
                    "Consider making this feature optional by setting `android:required=\"false\"` in your manifest."
                )
            }
        } else if (usesWifiPermission != null) {
            val location = context.client.xmlParser.getLocation(usesWifiPermission)
            context.report(
                ISSUE,
                location,
                "Looking for `android.hardware.wifi` but TV devices often use an Ethernet connection. " +
                "Consider making this feature optional by setting `android:required=\"false\"` in your manifest."
            )
        }
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"

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
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}