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
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"
        private const val HARDWARE_LEANBACK = "android.software.leanback"

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
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    // Track whether the merged manifest declares leanback and wifi features
    private var hasLeanback = false
    private var wifiElement: Element? = null
    private var wifiRequired = true
    private var wifiContext: XmlContext? = null

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return

        when (name) {
            HARDWARE_LEANBACK -> {
                hasLeanback = true
            }
            HARDWARE_WIFI -> {
                val requiredAttr = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                // If required attribute is absent, it defaults to true
                wifiRequired = requiredAttr.isNullOrEmpty() || requiredAttr == "true"
                wifiElement = element
                wifiContext = context
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        if (!hasLeanback) return
        val element = wifiElement ?: return
        val xmlContext = wifiContext ?: return

        if (wifiRequired) {
            val fix = LintFix.create()
                .set(ANDROID_URI, ATTR_REQUIRED, "false")
                .build()

            xmlContext.report(
                issue = ISSUE,
                scope = element,
                location = xmlContext.getLocation(element),
                message = "WiFi is not required for Android TV. Consider setting " +
                        "`android:required=\"false\"` for the `$HARDWARE_WIFI` feature, " +
                        "or removing the `uses-feature` tag entirely.",
                quickfixData = fix,
            )
        }
    }
}