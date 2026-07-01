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
import org.w3c.dom.Node
import java.util.EnumSet

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name != WIFI_FEATURE) {
            return
        }

        val required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED)
        if (required == SdkConstants.VALUE_FALSE) {
            return
        }

        if (!isTvManifest(context)) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "WiFi is not required for Android TV. Set `android:required=\"false\"` on this `<uses-feature>` declaration."
        )
    }

    private fun isTvManifest(context: XmlContext): Boolean {
        val manifest = context.document.documentElement ?: return false
        val elements = manifest.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val node = elements.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            val element = node as Element
            val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            when (element.tagName) {
                SdkConstants.TAG_USES_FEATURE -> {
                    if (name == TV_FEATURE || name == LEANBACK_FEATURE) {
                        return true
                    }
                }
                SdkConstants.TAG_CATEGORY -> {
                    if (name == SdkConstants.CATEGORY_LEANBACK_LAUNCHER) {
                        return true
                    }
                }
            }
        }
        return false
    }

    companion object {
        private const val WIFI_FEATURE = "android.hardware.wifi"
        private const val TV_FEATURE = "android.hardware.type.television"
        private const val LEANBACK_FEATURE = "android.software.leanback"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using `android.hardware.wifi` on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via \
                alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to \
                connect to the internet, please modify your Manifest to contain: \
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                EnumSet.of(Scope.MANIFEST_SCOPE)
            )
        )
    }
}