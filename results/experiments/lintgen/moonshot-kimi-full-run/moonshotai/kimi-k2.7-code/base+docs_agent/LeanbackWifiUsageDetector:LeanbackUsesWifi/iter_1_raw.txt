package com.android.tools.lint.checks

import com.android.SdkConstants
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

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_USES_FEATURE)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isLeanbackApp(context.document.documentElement ?: return)) {
            return
        }

        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name != HARDWARE_WIFI) {
            return
        }

        val required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED)
        if (required == SdkConstants.VALUE_FALSE) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Using `android.hardware.wifi` as required on Android TV; consider setting `android:required=\"false\"` if the app only needs general internet connectivity."
        )
    }

    private fun isLeanbackApp(manifest: Element): Boolean {
        val features = manifest.getElementsByTagName(SdkConstants.TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            val name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == HARDWARE_TYPE_TELEVISION) {
                return true
            }
        }

        val categories = manifest.getElementsByTagName(SdkConstants.TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? Element ?: continue
            val name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == CATEGORY_LEANBACK_LAUNCHER) {
                return true
            }
        }

        return false
    }

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"
        private const val HARDWARE_TYPE_TELEVISION = "android.hardware.type.television"
        private const val CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "WiFi hardware feature declared required on Android TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via \
                alternative methods such as Ethernet. If your app is not focused specifically on \
                WiFi functionality and only wishes to connect to the internet, please modify your \
                Manifest to contain: `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
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