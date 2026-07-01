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

    private var wifiRequiredElement: Element? = null
    private var wifiFeatureContext: XmlContext? = null
    private var hasLeanbackFeature = false

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return

        when (name) {
            "android.hardware.wifi" -> {
                val requiredAttr = element.getAttributeNS(ANDROID_URI, "required")
                // If required is absent or explicitly "true", this is a problem
                if (requiredAttr.isEmpty() || requiredAttr == "true") {
                    wifiRequiredElement = element
                    wifiFeatureContext = context
                }
            }
            "android.software.leanback" -> {
                hasLeanbackFeature = true
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        if (hasLeanbackFeature && wifiRequiredElement != null) {
            val xmlContext = wifiFeatureContext ?: return
            val element = wifiRequiredElement ?: return
            xmlContext.report(
                ISSUE,
                element,
                xmlContext.getLocation(element),
                "WiFi is not required for Android TV. Many TV devices connect via Ethernet. " +
                    "If your app only needs internet access (not WiFi specifically), change this to: " +
                    "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`. " +
                    "Un-metered or non-roaming connections can be detected using " +
                    "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                    "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using `android.hardware.wifi` on TV",
            explanation =
                "WiFi is not required for Android TV and many devices connect to the internet " +
                    "via alternative methods e.g. Ethernet.\n\n" +
                    "If your app is not focused specifically on WiFi functionality and only wishes " +
                    "to connect to the internet, please modify your Manifest to contain:\n" +
                    "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`\n\n" +
                    "Un-metered or non-roaming connections can be detected in software using " +
                    "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                    "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.",
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