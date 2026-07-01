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
import org.w3c.dom.Document
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name == "android.hardware.wifi") {
            val document = element.ownerDocument ?: return
            if (!usesLeanback(document)) {
                return
            }

            val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
            val isRequired = requiredAttr == null || requiredAttr.value != "false"
            if (isRequired) {
                val fix = fix()
                    .name("Set required=\"false\"")
                    .set()
                    .android()
                    .attribute(ATTR_REQUIRED)
                    .value("false")
                    .build()

                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Using `android.hardware.wifi` without `android:required=\"false\"` is not recommended for TV devices",
                    fix
                )
            }
        }
    }

    private fun usesLeanback(document: Document): Boolean {
        val usesFeatures = document.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until usesFeatures.length) {
            val item = usesFeatures.item(i) as? Element ?: continue
            val name = item.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.software.leanback") {
                return true
            }
        }
        return false
    }

    companion object {
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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}