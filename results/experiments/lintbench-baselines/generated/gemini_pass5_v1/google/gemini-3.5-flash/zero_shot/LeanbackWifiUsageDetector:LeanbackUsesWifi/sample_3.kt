package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    private var usesLeanback = false
    private var wifiFeatureElement: Element? = null
    private var wifiRequired = true

    override fun beforeCheckFile(context: Context) {
        usesLeanback = false
        wifiFeatureElement = null
        wifiRequired = true
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name == "android.software.leanback") {
            usesLeanback = true
        } else if (name == "android.hardware.wifi") {
            wifiFeatureElement = element
            val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, "required")
            if (requiredAttr != null) {
                wifiRequired = requiredAttr.value != "false"
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        val xmlContext = context as? XmlContext ?: return
        val element = wifiFeatureElement
        if (usesLeanback && element != null && wifiRequired) {
            val location = xmlContext.getNameLocation(element)
            val fix = fix()
                .set()
                .android()
                .attribute("required")
                .value("false")
                .build()

            xmlContext.report(
                ISSUE,
                element,
                location,
                "Expect `android:required=\"false\"` for `android.hardware.wifi` when `android.software.leanback` is used",
                fix
            )
        }
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