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
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        var wifiElement: Element? = null
        var hasLeanbackFeature = false
        var hasLeanbackLauncher = false

        val usesFeatures = document.getElementsByTagName("uses-feature")
        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as Element
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.hardware.wifi") {
                val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                if (required != "false") {
                    wifiElement = element
                }
            } else if (name == "android.software.leanback") {
                hasLeanbackFeature = true
            }
        }

        val categories = document.getElementsByTagName("category")
        for (i in 0 until categories.length) {
            val element = categories.item(i) as Element
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                hasLeanbackLauncher = true
            }
        }

        if (wifiElement != null && (hasLeanbackFeature || hasLeanbackLauncher)) {
            context.report(
                ISSUE,
                wifiElement,
                context.getNameLocation(wifiElement),
                "Using `android.hardware.wifi` on TV"
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