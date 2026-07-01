package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.mainProject.mergedManifest ?: return

        var usesLeanback = false
        var declaresWifiRequired = false
        var wifiNode: Element? = null

        val usesFeatures = mergedManifest.getElementsByTagName("uses-feature")
        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as? Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, "name")
            if (name == "android.software.leanback") {
                val required = element.getAttributeNS(ANDROID_URI, "required")
                if (required != "false") {
                    usesLeanback = true
                }
            } else if (name == "android.hardware.wifi") {
                val required = element.getAttributeNS(ANDROID_URI, "required")
                if (required != "false") {
                    declaresWifiRequired = true
                    wifiNode = element
                }
            }
        }

        if (!usesLeanback) {
            val categories = mergedManifest.getElementsByTagName("category")
            for (i in 0 until categories.length) {
                val element = categories.item(i) as? Element ?: continue
                val name = element.getAttributeNS(ANDROID_URI, "name")
                if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                    usesLeanback = true
                    break
                }
            }
        }

        if (usesLeanback && declaresWifiRequired && wifiNode != null) {
            val location = context.client.xmlParser.getLocation(wifiNode)
            context.report(
                ISSUE,
                location,
                "Using `android.hardware.wifi` on TV is not required and may " +
                        "prevent your app from being installed on devices without WiFi. " +
                        "Please set `android:required=\"false\"` for `android.hardware.wifi`."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain: `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using NetworkCapabilities#NET_CAPABILITY_NOT_METERED and NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.
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