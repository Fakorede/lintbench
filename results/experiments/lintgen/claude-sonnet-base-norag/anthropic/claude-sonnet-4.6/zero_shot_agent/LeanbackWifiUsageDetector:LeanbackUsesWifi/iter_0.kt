package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
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

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"

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

    /**
     * Tracks whether we have seen a `<uses-feature android:name="android.hardware.wifi">`
     * element that is explicitly marked `android:required="false"`.
     */
    private var wifiRequiredFalseFound = false

    /**
     * Tracks whether we have seen a `<uses-feature android:name="android.hardware.wifi">`
     * element that is required (either explicitly `true` or with no `required` attribute,
     * which defaults to `true`).
     */
    private var wifiRequiredElement: Element? = null
    private var wifiRequiredContext: XmlContext? = null

    override fun beforeCheckFile(context: Context) {
        wifiRequiredFalseFound = false
        wifiRequiredElement = null
        wifiRequiredContext = null
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != HARDWARE_WIFI) {
            return
        }

        val requiredAttr = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
        if (requiredAttr.equals("false", ignoreCase = true)) {
            wifiRequiredFalseFound = true
        } else {
            // Either explicitly "true" or absent (defaults to true)
            wifiRequiredElement = element
            wifiRequiredContext = context
        }
    }

    override fun afterCheckFile(context: Context) {
        // Only report if there is a wifi uses-feature that is required=true (or defaulted)
        // and there is no counterpart with required=false.
        val element = wifiRequiredElement ?: return
        val xmlContext = wifiRequiredContext ?: return

        if (!wifiRequiredFalseFound) {
            xmlContext.report(
                issue = ISSUE,
                location = xmlContext.getElementLocation(element),
                message = "Requiring `android.hardware.wifi` is not recommended for Android TV " +
                    "apps; many TV devices connect via Ethernet or other means. Consider " +
                    "adding `android:required=\"false\"` or removing this `<uses-feature>` " +
                    "declaration if WiFi is not essential to your app."
            )
        }
    }
}