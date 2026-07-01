package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private const val TAG_USES_FEATURE = "uses-feature"
        private const val TAG_CATEGORY = "category"
        private const val ATTR_NAME = "name"
        private const val ATTR_REQUIRED = "required"
        private const val WIFI_FEATURE = "android.hardware.wifi"
        private const val LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.MERGED_MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via
                alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to
                connect to the internet, please modify your Manifest to contain:

                <uses-feature android:name="android.hardware.wifi" android:required="false" />

                Un-metered or non-roaming connections can be detected in software using
                NetworkCapabilities#NET_CAPABILITY_NOT_METERED and
                NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        // Detection is performed during XML scanning of the merged manifest.
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_USES_FEATURE)

    override fun getApplicableAttributes(): Collection<String>? =
        null

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (!isTvApp(context)) return

        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != WIFI_FEATURE) return

        val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
        if (required.isNotEmpty() && !required.toBoolean()) return

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Using `android.hardware.wifi` required on a TV app; " +
                    "consider setting `android:required=\"false\"` if Wi-Fi is not essential."
        )
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) { }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) { }

    private fun isTvApp(context: XmlContext): Boolean {
        val categories = context.document.getElementsByTagName(TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? org.w3c.dom.Element ?: continue
            if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == LEANBACK_LAUNCHER) {
                return true
            }
        }
        return false
    }
}