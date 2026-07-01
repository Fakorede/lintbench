package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    private var hasLeanback = false
    private var hasWifiRequired = false
    private var wifiFeatureElement: org.w3c.dom.Element? = null
    private var wifiContext: XmlContext? = null

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

    override fun getApplicableElements(): Collection<String>? {
        return listOf("uses-feature", "category")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name")
        if (element.tagName == "uses-feature") {
            if (name == "android.software.leanback") {
                hasLeanback = true
            } else if (name == "android.hardware.wifi") {
                val required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required")
                if (required != "false") {
                    hasWifiRequired = true
                    wifiFeatureElement = element
                    wifiContext = context
                }
            }
        } else if (element.tagName == "category") {
            if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                hasLeanback = true
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        if (hasLeanback && hasWifiRequired) {
            val element = wifiFeatureElement
            val xmlCtx = wifiContext
            if (element != null && xmlCtx != null) {
                val location = xmlCtx.getLocation(element)
                xmlCtx.report(
                    ISSUE,
                    element,
                    location,
                    "Using `android.hardware.wifi` on TV is not recommended"
                )
            }
        }
    }
}