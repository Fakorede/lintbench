package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.VALUE_FALSE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.getAttributeNS(ANDROID_URI, ATTR_NAME) != WIFI_FEATURE) {
            return
        }

        if (!context.document.isLeanbackApp()) {
            return
        }

        if (element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED) == VALUE_FALSE) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Using `android.hardware.wifi` on Android TV; consider setting `android:required=\"false\"`"
        )
    }

    private fun Document.isLeanbackApp(): Boolean {
        val root = documentElement ?: return false
        if (root.tagName != TAG_MANIFEST) {
            return false
        }

        val features = root.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            if (feature.getAttributeNS(ANDROID_URI, ATTR_NAME) == LEANBACK_FEATURE) {
                return true
            }
        }

        val categories = root.getElementsByTagName(TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? Element ?: continue
            if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == LEANBACK_LAUNCHER_CATEGORY) {
                return true
            }
        }

        return false
    }

    companion object {
        private const val WIFI_FEATURE = "android.hardware.wifi"
        private const val LEANBACK_FEATURE = "android.software.leanback"
        private const val LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER"

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using `android.hardware.wifi` on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
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