package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val mergedManifest = context.mainProject.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        var usesLeanback = false
        var declaresWifi = false
        var wifiRequired = true
        var wifiNode: Element? = null

        val features = root.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val element = features.item(i) as? Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.software.leanback") {
                usesLeanback = true
            } else if (name == "android.hardware.wifi") {
                declaresWifi = true
                wifiNode = element
                val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                if (requiredAttr != null && requiredAttr.value == "false") {
                    wifiRequired = false
                }
            }
        }

        if (!usesLeanback) {
            val categories = root.getElementsByTagName("category")
            for (i in 0 until categories.length) {
                val element = categories.item(i) as? Element ?: continue
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                    usesLeanback = true
                    break
                }
            }
        }

        if (usesLeanback && declaresWifi && wifiRequired && wifiNode != null) {
            val location = context.client.getXmlParser().getLocation(context, wifiNode)
            context.report(
                ISSUE,
                location,
                "Using `android.hardware.wifi` without `android:required=\"false\"` is not recommended for Android TV"
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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(LeanbackWifiUsageDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}