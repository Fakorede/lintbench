package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: XmlContext) {
        val document = context.document ?: return
        val root = document.documentElement ?: return

        var isTvApp = false
        var wifiFeature: Element? = null

        var child: Node? = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "uses-feature") {
                val element = child as Element
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val required = element.getAttributeNS(ANDROID_URI, "required")
                val isRequired = required != "false"

                if (isRequired) {
                    if (name == "android.software.leanback" || name == "android.hardware.type.television") {
                        isTvApp = true
                    }
                }
                if (name == "android.hardware.wifi") {
                    wifiFeature = element
                }
            }
            child = child.nextSibling
        }

        if (isTvApp && wifiFeature != null) {
            val requiredAttr = wifiFeature.getAttributeNodeNS(ANDROID_URI, "required")
            val isWifiRequired = requiredAttr == null || requiredAttr.value != "false"
            if (isWifiRequired) {
                val location = if (requiredAttr != null) {
                    context.getValueLocation(requiredAttr)
                } else {
                    context.getLocation(wifiFeature)
                }
                context.report(
                    ISSUE,
                    wifiFeature,
                    location,
                    "WiFi is not required for Android TV and many devices connect to the internet via " +
                    "alternative methods e.g. Ethernet. If your app is not focused specifically on WiFi " +
                    "functionality and only wishes to connect to the internet, please modify your Manifest " +
                    "to contain: `<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`. " +
                    "Un-metered or non-roaming connections can be detected in software using " +
                    "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = "WiFi is not required for Android TV and many devices connect to the internet via " +
                "alternative methods e.g. Ethernet.\n\n" +
                "If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, " +
                "please modify your Manifest to contain: " +
                "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`\n\n" +
                "Un-metered or non-roaming connections can be detected in software using " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(LeanbackWifiUsageDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }
}