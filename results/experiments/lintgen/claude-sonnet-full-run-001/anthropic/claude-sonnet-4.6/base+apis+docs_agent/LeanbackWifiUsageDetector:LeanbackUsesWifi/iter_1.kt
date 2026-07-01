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
        private const val HARDWARE_WIFI = "android.hardware.wifi"
        private const val SOFTWARE_LEANBACK = "android.software.leanback"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using `android.hardware.wifi` on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet \
                via alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes \
                to connect to the internet, please modify your Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != HARDWARE_WIFI) {
            return
        }

        val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
        // If android:required is explicitly set to "false", this is fine
        if (requiredAttr != null && requiredAttr.value == "false") {
            return
        }

        // Check if the project targets TV (has leanback feature in manifest)
        if (!isLeanbackProject(context)) {
            return
        }

        val location = if (requiredAttr != null) {
            context.getValueLocation(requiredAttr)
        } else {
            context.getElementLocation(element)
        }

        context.report(
            issue = ISSUE,
            location = location,
            message = "Requiring `android.hardware.wifi` is not recommended for Android TV apps " +
                "as many TV devices connect via Ethernet. Consider setting " +
                "`android:required=\"false\"` unless your app specifically requires WiFi."
        )
    }

    private fun isLeanbackProject(context: XmlContext): Boolean {
        // Parse the manifest to look for leanback uses-feature
        val document = context.document
        val root = document.documentElement ?: return false
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == TAG_USES_FEATURE) {
                val featureName = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (featureName == SOFTWARE_LEANBACK) {
                    return true
                }
            }
        }

        return false
    }
}