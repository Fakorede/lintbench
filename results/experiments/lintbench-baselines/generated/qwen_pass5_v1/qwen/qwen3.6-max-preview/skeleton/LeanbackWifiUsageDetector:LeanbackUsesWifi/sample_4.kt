package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), Detector.XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.MANIFEST_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = "WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet.\n\n" +
                "If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain: " +
                "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`\n\n" +
                "Un-metered or non-roaming connections can be detected in software using " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"
        private const val ATTR_REQUIRED = "required"
        private const val WIFI_FEATURE = "android.hardware.wifi"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("uses-feature")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name == WIFI_FEATURE) {
            val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
            if (required != "false") {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WiFi is not required for Android TV. Set `android:required=\"false\"` if WiFi is not strictly needed."
                )
            }
        }
    }
}