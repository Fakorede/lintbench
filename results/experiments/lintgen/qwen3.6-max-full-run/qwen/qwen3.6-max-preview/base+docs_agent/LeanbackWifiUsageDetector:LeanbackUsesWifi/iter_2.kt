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

    override fun getApplicableElements(): Collection<String>? = listOf(
        SdkConstants.TAG_USES_FEATURE,
        SdkConstants.TAG_USES_PERMISSION
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        val tag = element.tagName

        if (tag == SdkConstants.TAG_USES_FEATURE) {
            if (name == "android.hardware.wifi") {
                val required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED)
                if (required != "false") {
                    context.report(
                        ISSUE,
                        context.getLocation(element),
                        "WiFi is not required for Android TV. Set android:required=\"false\" for android.hardware.wifi."
                    )
                }
            }
        } else if (tag == SdkConstants.TAG_USES_PERMISSION) {
            if (name == "android.permission.ACCESS_WIFI_STATE" ||
                name == "android.permission.CHANGE_WIFI_STATE" ||
                name == "android.permission.CHANGE_WIFI_MULTICAST_STATE"
            ) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Using $name implies a requirement for android.hardware.wifi. " +
                        "Consider using ConnectivityManager instead, or explicitly declare " +
                        "<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet.
                If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain: `<uses-feature android:name="android.hardware.wifi" android:required="false" />`
                Un-metered or non-roaming connections can be detected in software using `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
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