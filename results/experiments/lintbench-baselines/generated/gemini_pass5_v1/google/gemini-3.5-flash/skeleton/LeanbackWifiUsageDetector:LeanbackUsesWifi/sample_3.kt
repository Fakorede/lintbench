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
        val mergedManifest = context.mainProject.mergedManifest ?: return
        if (!isLeanbackApp(mergedManifest)) {
            return
        }
        val wifiFeatureNode = findWifiFeatureNode(mergedManifest) ?: return
        val requiredStr = getAndroidAttribute(wifiFeatureNode, "required")
        val required = requiredStr.isEmpty() || requiredStr == "true"
        if (required) {
            context.report(
                ISSUE,
                context.getLocation(wifiFeatureNode),
                "Using `android.hardware.wifi` without `android:required=\"false\"` is not recommended on Android TV"
            )
        }
    }

    private fun isLeanbackApp(manifest: org.w3c.dom.Document): Boolean {
        val root = manifest.documentElement ?: return false

        val usesFeatures = root.getElementsByTagName("uses-feature")
        for (i in 0 until usesFeatures.length) {
            val item = usesFeatures.item(i) as? org.w3c.dom.Element ?: continue
            val name = getAndroidAttribute(item, "name")
            if (name == "android.software.leanback") {
                return true
            }
        }

        val categories = root.getElementsByTagName("category")
        for (i in 0 until categories.length) {
            val item = categories.item(i) as? org.w3c.dom.Element ?: continue
            val name = getAndroidAttribute(item, "name")
            if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                return true
            }
        }

        return false
    }

    private fun findWifiFeatureNode(manifest: org.w3c.dom.Document): org.w3c.dom.Element? {
        val root = manifest.documentElement ?: return null
        val usesFeatures = root.getElementsByTagName("uses-feature")
        for (i in 0 until usesFeatures.length) {
            val item = usesFeatures.item(i) as? org.w3c.dom.Element ?: continue
            val name = getAndroidAttribute(item, "name")
            if (name == "android.hardware.wifi") {
                return item
            }
        }
        return null
    }

    private fun getAndroidAttribute(element: org.w3c.dom.Element, localName: String): String {
        return element.getAttributeNS("http://schemas.android.com/apk/res/android", localName).takeIf { it.isNotEmpty() }
            ?: element.getAttribute("android:$localName")
    }
}