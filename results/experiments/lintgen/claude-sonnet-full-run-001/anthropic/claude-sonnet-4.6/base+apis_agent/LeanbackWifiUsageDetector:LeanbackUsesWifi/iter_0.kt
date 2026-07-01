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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"

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
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (name != HARDWARE_WIFI) return

        // Check if android:required is explicitly set to false
        val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
        if (requiredAttr != null && requiredAttr.value == "false") {
            // android:required="false" is set — no issue
            return
        }

        // Check if the project targets TV (has leanback feature or uses leanback library)
        if (!isLeanbackProject(context)) return

        val fix = fix()
            .set(ANDROID_URI, ATTR_REQUIRED, "false")
            .build()

        context.report(
            issue = ISSUE,
            scope = element,
            location = context.getNameLocation(element),
            message = "Requiring `android.hardware.wifi` is not recommended for Android TV apps; " +
                    "many TV devices connect via Ethernet. Consider setting " +
                    "`android:required=\"false\"`.",
            quickfixData = fix
        )
    }

    private fun isLeanbackProject(context: XmlContext): Boolean {
        val project = context.project
        // Check if the manifest declares the leanback uses-feature
        val mainManifest = project.mergedManifest ?: return false
        val documentElement = mainManifest.documentElement ?: return false
        val children = documentElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            if (node.tagName == TAG_USES_FEATURE) {
                val featureName = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (featureName == "android.software.leanback") {
                    return true
                }
            }
        }
        // Also check dependencies for leanback library
        return project.dependsOn("androidx.leanback:leanback") == true ||
                project.dependsOn("com.android.support:leanback-v17") == true
    }
}