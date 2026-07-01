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

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_USES_FEATURE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isTvApp(context)) {
            return
        }

        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name != SdkConstants.FEATURE_WIFI) {
            return
        }

        val required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED)
        if (required.isBlank() || required != SdkConstants.VALUE_FALSE) {
            val attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            val location = if (attr != null) context.getLocation(attr) else context.getLocation(element)
            context.report(
                ISSUE,
                location,
                "Using `android.hardware.wifi` on TV; consider `android:required=\"false\"` if you only need internet connectivity"
            )
        }
    }

    private fun isTvApp(context: XmlContext): Boolean {
        val manifest = context.project?.manifest ?: return false
        val features = manifest.documentElement?.getElementsByTagName(SdkConstants.TAG_USES_FEATURE) ?: return false
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            if (feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME) == SdkConstants.FEATURE_LEANBACK) {
                val required = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED)
                if (required.isBlank() || required != SdkConstants.VALUE_FALSE) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV; many devices connect to the internet via alternative
                methods such as Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to connect
                to the internet, modify your AndroidManifest.xml to contain:

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