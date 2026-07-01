package com.android.tools.lint.checks

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.XmlParser
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
            Scope.MANIFEST_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via
                alternative methods such as Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to
                connect to the internet, please modify your Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableFiles() = Scope.MANIFEST_SCOPE

    override fun checkMergedProject(context: Context) {
        val client: LintClient = context.client
        val manifest = client.getMergedManifest(context.project) ?: return
        if (!manifest.exists()) return

        val parsed = client.xmlParser.parseXml(client.readFile(manifest), manifest) ?: return
        val root: org.w3c.dom.Element = when (parsed) {
            is org.w3c.dom.Document -> parsed.documentElement ?: return
            is org.w3c.dom.Element -> parsed
            else -> return
        }

        val wifiFeatures = mutableListOf<org.w3c.dom.Element>()
        val leanbackCategories = mutableListOf<org.w3c.dom.Element>()
        collectRelevantElements(root, wifiFeatures, leanbackCategories)

        if (wifiFeatures.isNotEmpty() && leanbackCategories.isNotEmpty()) {
            context.report(ISSUE, Location.create(manifest), REPORT_MESSAGE)
        }
    }

    private fun collectRelevantElements(
        element: org.w3c.dom.Element,
        wifiFeatures: MutableList<org.w3c.dom.Element>,
        leanbackCategories: MutableList<org.w3c.dom.Element>
    ) {
        when (element.tagName) {
            TAG_USES_FEATURE -> {
                val name = element.getAttributeNS(NS_ANDROID, ATTR_NAME)
                if (name == NAME_WIFI && element.getAttributeNS(NS_ANDROID, ATTR_REQUIRED) != VALUE_FALSE) {
                    wifiFeatures.add(element)
                }
            }
            TAG_CATEGORY -> {
                val name = element.getAttributeNS(NS_ANDROID, ATTR_NAME)
                if (name == CATEGORY_LEANBACK_LAUNCHER) {
                    leanbackCategories.add(element)
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is org.w3c.dom.Element) {
                collectRelevantElements(child, wifiFeatures, leanbackCategories)
            }
        }
    }
}

private const val NS_ANDROID = "http://schemas.android.com/apk/res/android"
private const val NAME_WIFI = "android.hardware.wifi"
private const val CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"
private const val TAG_USES_FEATURE = "uses-feature"
private const val TAG_CATEGORY = "category"
private const val ATTR_NAME = "name"
private const val ATTR_REQUIRED = "required"
private const val VALUE_FALSE = "false"
private const val REPORT_MESSAGE =
    "Using android.hardware.wifi on TV; consider setting android:required=\"false\" unless your app specifically requires Wi-Fi"