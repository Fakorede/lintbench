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
                connect to the internet, please modify your Manifest to contain:
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Only care about uses-feature elements
        if (element.tagName != TAG_USES_FEATURE) return

        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (name != HARDWARE_WIFI) return

        // Check if android:required is explicitly set to false
        val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
        if (requiredAttr != null && requiredAttr.value == "false") {
            // Already marked as not required, no issue
            return
        }

        // Check if the manifest targets leanback (TV)
        if (!isLeanbackApp(context)) return

        // Report the issue
        val fix = if (requiredAttr != null) {
            // There's already a required attribute but it's not false — fix its value
            LintFix.create()
                .set(ANDROID_URI, ATTR_REQUIRED, "false")
                .build()
        } else {
            // No required attribute — add one
            LintFix.create()
                .set(ANDROID_URI, ATTR_REQUIRED, "false")
                .build()
        }

        context.report(
            issue = ISSUE,
            location = context.getElementLocation(element),
            message = "Requiring `android.hardware.wifi` is not recommended for Android TV apps " +
                    "as many TV devices connect via Ethernet. Consider setting " +
                    "`android:required=\"false\"` unless your app specifically requires WiFi.",
            quickfixData = fix,
        )
    }

    private fun isLeanbackApp(context: XmlContext): Boolean {
        val document = context.document ?: return false
        val root = document.documentElement ?: return false

        val usesFeatures = root.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as? Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: continue
            if (name == HARDWARE_LEANBACK) {
                return true
            }
        }
        return false
    }

    override fun checkMergedProject(context: Context) {
        // No-op: all checking is done per-element in visitElement
    }
}