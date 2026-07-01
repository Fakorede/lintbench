package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
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
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return

        if (name != HARDWARE_FEATURE_WIFI) {
            return
        }

        // Check if android:required is explicitly set to false
        val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
        if (requiredAttr != null && requiredAttr.value == "false") {
            // android:required="false" is already set, no issue
            return
        }

        // Report the issue - either required is true or not specified (defaults to true)
        val fix = fix()
            .name("Set android:required=\"false\"")
            .set(ANDROID_URI, ATTR_REQUIRED, "false")
            .build()

        val location = if (requiredAttr != null) {
            context.getValueLocation(requiredAttr)
        } else {
            context.getNameLocation(element)
        }

        context.report(
            issue = ISSUE,
            element = element,
            location = location,
            message = MESSAGE,
            quickfixData = fix
        )
    }

    companion object {
        private const val HARDWARE_FEATURE_WIFI = "android.hardware.wifi"

        private const val MESSAGE =
            "WiFi is not required for Android TV. Many TV devices connect to the internet " +
                "via alternative methods such as Ethernet. If your app is not focused " +
                "specifically on WiFi functionality and only wishes to connect to the internet, " +
                "please modify your Manifest to contain: " +
                "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`. " +
                "Un-metered or non-roaming connections can be detected in software using " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`."

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using `android.hardware.wifi` on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet \
                via alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes \
                to connect to the internet, please modify your Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}