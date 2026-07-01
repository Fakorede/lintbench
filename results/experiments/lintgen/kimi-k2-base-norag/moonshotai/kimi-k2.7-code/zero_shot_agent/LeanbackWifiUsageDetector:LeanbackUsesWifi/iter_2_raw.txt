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

    override fun getApplicableElements(): Collection<String> = listOf("uses-feature", "uses-permission")

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name.isEmpty()) {
            return
        }

        val message = when (element.tagName) {
            "uses-feature" -> {
                if (name != WIFI_FEATURE) {
                    return
                }
                if (element.getAttributeNS(ANDROID_URI, "required") == VALUE_FALSE) {
                    return
                }
                "Using $name on TV"
            }
            "uses-permission" -> {
                if (name !in WIFI_PERMISSIONS) {
                    return
                }
                "Using $name on TV"
            }
            else -> return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            message
        )
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val VALUE_FALSE = "false"
        private const val WIFI_FEATURE = "android.hardware.wifi"
        private val WIFI_PERMISSIONS = setOf(
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via
                alternative methods e.g. Ethernet.

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
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}