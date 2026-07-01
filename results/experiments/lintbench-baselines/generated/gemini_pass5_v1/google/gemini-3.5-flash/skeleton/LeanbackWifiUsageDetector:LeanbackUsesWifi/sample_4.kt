package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.MANIFEST_SCOPE,
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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.project.mergedManifest ?: return

        var hasLeanbackFeature = false
        var hasLeanbackLauncher = false
        var wifiFeatureRequired: Boolean? = null
        var hasWifiPermission = false

        val usesFeatures = mergedManifest.getElementsByTagName("uses-feature")
        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as? org.w3c.dom.Element ?: continue
            val name = getAndroidAttribute(element, "name")
            if (name == "android.software.leanback") {
                hasLeanbackFeature = true
            } else if (name == "android.hardware.wifi") {
                val requiredStr = getAndroidAttribute(element, "required")
                wifiFeatureRequired = requiredStr != "false"
            }
        }

        val categories = mergedManifest.getElementsByTagName("category")
        for (i in 0 until categories.length) {
            val element = categories.item(i) as? org.w3c.dom.Element ?: continue
            val name = getAndroidAttribute(element, "name")
            if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                hasLeanbackLauncher = true
            }
        }

        val usesPermissions = mergedManifest.getElementsByTagName("uses-permission")
        for (i in 0 until usesPermissions.length) {
            val element = usesPermissions.item(i) as? org.w3c.dom.Element ?: continue
            val name = getAndroidAttribute(element, "name")
            if (name == "android.permission.ACCESS_WIFI_STATE" ||
                name == "android.permission.CHANGE_WIFI_STATE" ||
                name == "android.permission.CHANGE_WIFI_MULTICAST_STATE") {
                hasWifiPermission = true
            }
        }

        val isTvApp = hasLeanbackFeature || hasLeanbackLauncher
        val requiresWifi = if (wifiFeatureRequired != null) {
            wifiFeatureRequired
        } else {
            hasWifiPermission
        }

        if (isTvApp && requiresWifi) {
            val mainManifestFile = context.project.manifestFiles.firstOrNull() ?: return
            val location = Location.create(mainManifestFile)
            context.report(
                ISSUE,
                location,
                "Using `android.hardware.wifi` on TV without `android:required=\"false\"` is not recommended."
            )
        }
    }

    private fun getAndroidAttribute(element: org.w3c.dom.Element, localName: String): String {
        val value = element.getAttributeNS("http://schemas.android.com/apk/res/android", localName)
        if (value.isNotEmpty()) return value
        return element.getAttribute("android:$localName")
    }
}