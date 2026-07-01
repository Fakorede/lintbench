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
        val mergedManifest = context.mainProject.mergedManifest ?: return
        
        var usesLeanback = false
        var wifiFeatureNode: org.w3c.dom.Element? = null
        
        val usesFeatures = mergedManifest.getElementsByTagName("uses-feature")
        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as org.w3c.dom.Element
            val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            if (name == "android.software.leanback") {
                usesLeanback = true
            } else if (name == "android.hardware.wifi") {
                wifiFeatureNode = element
            }
        }
        
        if (usesLeanback && wifiFeatureNode != null) {
            val requiredAttribute = wifiFeatureNode.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "required")
            val required = requiredAttribute?.value != "false"
            if (required) {
                val location = context.getLocation(wifiFeatureNode)
                context.report(
                    ISSUE,
                    wifiFeatureNode,
                    location,
                    "Using `android.hardware.wifi` on TV"
                )
            }
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

                Un-metered or non-roaming connections can be detected in software using `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(LeanbackWifiUsageDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}