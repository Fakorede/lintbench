package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain: `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val WIFI_FEATURE = "android.hardware.wifi"
        private const val LEANBACK_FEATURE = "android.software.leanback"
        private const val LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"
    }

    override fun getApplicableElements(): Collection<String>? = listOf("uses-feature")

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name != WIFI_FEATURE) return

        val required = element.getAttributeNS(ANDROID_URI, "required")
        if (required == "false") return

        if (!isLeanbackApp(context.document)) return

        context.report(
            ISSUE,
            context.getLocation(element),
            "WiFi is not required for Android TV. Set android:required=\"false\" for $WIFI_FEATURE."
        )
    }

    private fun isLeanbackApp(document: Document): Boolean {
        val root = document.documentElement ?: return false

        val features = root.getElementsByTagName("uses-feature")
        for (i in 0 until features.length) {
            val feat = features.item(i) as? Element ?: continue
            if (feat.getAttributeNS(ANDROID_URI, "name") == LEANBACK_FEATURE) return true
        }

        val categories = root.getElementsByTagName("category")
        for (i in 0 until categories.length) {
            val cat = categories.item(i) as? Element ?: continue
            if (cat.getAttributeNS(ANDROID_URI, "name") == LEANBACK_LAUNCHER) return true
        }

        return false
    }
}