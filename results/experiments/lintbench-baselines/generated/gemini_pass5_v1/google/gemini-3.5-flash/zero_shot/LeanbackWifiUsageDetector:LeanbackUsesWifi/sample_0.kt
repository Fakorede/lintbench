package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), Detector.XmlScanner {

    private var hasLeanback = false
    private var wifiFeatureElement: Element? = null

    override fun getApplicableElements(): Collection<String> {
        return listOf("uses-feature", "category")
    }

    override fun beforeCheckFile(context: XmlContext) {
        hasLeanback = false
        wifiFeatureElement = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        when (element.tagName) {
            "uses-feature" -> {
                if (name == "android.software.leanback") {
                    hasLeanback = true
                } else if (name == "android.hardware.wifi") {
                    val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                    if (required != "false") {
                        wifiFeatureElement = element
                    }
                }
            }
            "category" -> {
                if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                    hasLeanback = true
                }
            }
        }
    }

    override fun afterCheckFile(context: XmlContext) {
        val wifiElement = wifiFeatureElement
        if (hasLeanback && wifiElement != null) {
            val fix = fix()
                .set()
                .android()
                .attribute(ATTR_REQUIRED)
                .value("false")
                .build()

            context.report(
                ISSUE,
                wifiElement,
                context.getNameLocation(wifiElement),
                "WiFi is not required for Android TV and many devices connect via Ethernet. " +
                        "Please set `android:required=\"false\"` for `android.hardware.wifi`.",
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