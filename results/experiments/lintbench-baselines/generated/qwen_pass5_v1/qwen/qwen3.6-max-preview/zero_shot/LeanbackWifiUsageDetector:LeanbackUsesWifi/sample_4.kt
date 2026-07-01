package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.USES_FEATURE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via  alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to  connect to the internet, please modify your Manifest to contain:  `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using  `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and  `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val WIFI_FEATURE = "android.hardware.wifi"
        private const val LEANBACK_FEATURE = "android.software.leanback"
        private const val LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != WIFI_FEATURE) return

        val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
        if (required == "false") return

        if (!isTvApp(context.document)) return

        val message = "WiFi hardware feature should not be required on Android TV. " +
                "Set android:required=\"false\" or remove the uses-feature declaration."
        context.report(ISSUE, context.getLocation(element), message)
    }

    private fun isTvApp(document: Document): Boolean {
        val root = document.documentElement ?: return false

        val features = root.getElementsByTagName(USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            if (feature.getAttributeNS(ANDROID_URI, ATTR_NAME) == LEANBACK_FEATURE) {
                return true
            }
        }

        val categories = root.getElementsByTagName(TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val cat = categories.item(i) as? Element ?: continue
            if (cat.getAttributeNS(ANDROID_URI, ATTR_NAME) == LEANBACK_LAUNCHER_CATEGORY) {
                return true
            }
        }

        return false
    }
}