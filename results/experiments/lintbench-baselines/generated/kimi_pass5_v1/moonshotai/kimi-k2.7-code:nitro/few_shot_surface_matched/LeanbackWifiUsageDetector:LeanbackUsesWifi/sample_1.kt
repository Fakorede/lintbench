package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.CATEGORY_LEANBACK_LAUNCHER
import com.android.SdkConstants.TAG_CATEGORY
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

    private var isTvApp = false

    override fun checkMergedProject(): Boolean = true

    override fun beforeCheckFile(context: XmlContext) {
        isTvApp = false
        val root = context.document.documentElement ?: return
        isTvApp = hasLeanbackFeature(root) || hasLeanbackLauncher(root)
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isTvApp) return

        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (nameAttr.value != WIFI_FEATURE) return

        val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
        if (requiredAttr == null || requiredAttr.value != "false") {
            val location = requiredAttr?.let { context.getValueLocation(it) }
                ?: context.getElementLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "Using android.hardware.wifi on Android TV: WiFi is not required for TV. " +
                    "If your app only needs internet access, set android:required=\"false\"."
            )
        }
    }

    private fun hasLeanbackFeature(root: Element): Boolean {
        val features = root.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            val name = feature.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == LEANBACK_FEATURE || name == TV_FEATURE) {
                return true
            }
        }
        return false
    }

    private fun hasLeanbackLauncher(root: Element): Boolean {
        val categories = root.getElementsByTagName(TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? Element ?: continue
            val name = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == CATEGORY_LEANBACK_LAUNCHER) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val WIFI_FEATURE = "android.hardware.wifi"
        private const val LEANBACK_FEATURE = "android.software.leanback"
        private const val TV_FEATURE = "android.hardware.type.television"

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = "WiFi is not required for Android TV and many devices connect to the " +
                "internet via alternative methods, e.g. Ethernet.\n\n" +
                "If your app is not focused specifically on WiFi functionality and only wishes " +
                "to connect to the internet, modify your manifest to contain:\n" +
                "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`\n\n" +
                "Unmetered or non-roaming connections can be detected in software using " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.",
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