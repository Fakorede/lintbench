package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_USES_FEATURE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.isLeanbackApp()) return

        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name != WIFI_HARDWARE) return

        val requiredAttr = element.getAttributeNodeNS(
            SdkConstants.ANDROID_URI,
            SdkConstants.ATTR_REQUIRED
        )

        val requiredTrue = requiredAttr?.value != SdkConstants.VALUE_FALSE
        if (!requiredTrue) return

        val location = requiredAttr?.let { context.getLocation(it) }
            ?: context.getElementLocation(element)

        context.report(
            ISSUE,
            element,
            location,
            MESSAGE
        )
    }

    private fun XmlContext.isLeanbackApp(): Boolean {
        val features = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            val name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            val required = feature.getAttributeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_REQUIRED
            ) != SdkConstants.VALUE_FALSE

            if (name == LEANBACK_FEATURE && required) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val WIFI_HARDWARE = "android.hardware.wifi"
        private const val LEANBACK_FEATURE = "android.software.leanback"

        private const val MESSAGE =
            "Using `android.hardware.wifi` on TV without marking it as not required"

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}