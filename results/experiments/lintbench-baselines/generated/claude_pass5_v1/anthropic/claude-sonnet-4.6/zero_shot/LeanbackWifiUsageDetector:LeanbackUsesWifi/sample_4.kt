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
import java.util.EnumSet

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
                EnumSet.of(Scope.MANIFEST)
            )
        )

        private const val TAG_USES_FEATURE_LOCAL = TAG_USES_FEATURE
    }

    /**
     * Tracks whether we have seen a <uses-feature android:name="android.hardware.wifi"
     * android:required="false" /> declaration.
     */
    private var wifiRequiredFalse = false

    /**
     * Tracks the element where android.hardware.wifi is declared as required (or without
     * an explicit required="false"), so we can report it at the end if needed.
     */
    private var wifiRequiredElement: Element? = null
    private var wifiRequiredContext: XmlContext? = null

    override fun beforeCheckFile(context: Context) {
        wifiRequiredFalse = false
        wifiRequiredElement = null
        wifiRequiredContext = null
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE_LOCAL)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (name != HARDWARE_WIFI) return

        val requiredAttr = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
        if (requiredAttr.equals("false", ignoreCase = true)) {
            // Explicitly marked as not required — this is acceptable
            wifiRequiredFalse = true
            wifiRequiredElement = null
            wifiRequiredContext = null
        } else {
            // Either required="true" or the attribute is absent (defaults to true)
            if (!wifiRequiredFalse) {
                wifiRequiredElement = element
                wifiRequiredContext = context
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        val element = wifiRequiredElement ?: return
        val xmlContext = wifiRequiredContext ?: return

        if (!wifiRequiredFalse) {
            val location = xmlContext.getElementLocation(element)
            xmlContext.report(
                ISSUE,
                element,
                location,
                "Requiring `android.hardware.wifi` limits app availability on TV. " +
                    "Consider adding `android:required=\"false\"` if your app does not " +
                    "strictly require WiFi."
            )
        }
    }
}