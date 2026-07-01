package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("uses-feature")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name == "android.hardware.wifi") {
            val required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required")
            if (required != "false") {
                if (hasLeanbackFeature(element)) {
                    val location = context.getNameLocation(element)
                    context.report(
                        ISSUE,
                        element,
                        location,
                        "Expecting `android:required=\"false\"` for `android.hardware.wifi` when running on TV"
                    )
                }
            }
        }
    }

    private fun hasLeanbackFeature(element: Element): Boolean {
        val root = element.ownerDocument.documentElement ?: return false
        val nodeList = root.getElementsByTagName("uses-feature")
        for (i in 0 until nodeList.length) {
            val feature = nodeList.item(i) as? Element ?: continue
            val name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == "android.software.leanback") {
                return true
            }
        }
        return false
    }

    companion object {
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