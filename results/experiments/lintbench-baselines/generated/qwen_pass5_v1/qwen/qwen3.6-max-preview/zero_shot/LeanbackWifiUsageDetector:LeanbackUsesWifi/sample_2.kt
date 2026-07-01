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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = "WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet.\n\n" +
                "If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain: " +
                "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`\n\n" +
                "Un-metered or non-roaming connections can be detected in software using " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_USES_FEATURE)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != "android.hardware.wifi") return

        if (!isTvApp(context)) return

        val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
        if (requiredAttr == null || requiredAttr.value != "false") {
            context.report(
                ISSUE,
                context.getLocation(element),
                "WiFi is not required for Android TV. Set `android:required=\"false\"` for `android.hardware.wifi`."
            )
        }
    }

    private fun isTvApp(context: XmlContext): Boolean {
        val root = context.document.documentElement ?: return false
        val features = root.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as Element
            val featureName = feature.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (featureName == "android.software.leanback" || featureName == "android.hardware.type.television") {
                val reqAttr = feature.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                if (reqAttr == null || reqAttr.value != "false") {
                    return true
                }
            }
        }
        return false
    }
}