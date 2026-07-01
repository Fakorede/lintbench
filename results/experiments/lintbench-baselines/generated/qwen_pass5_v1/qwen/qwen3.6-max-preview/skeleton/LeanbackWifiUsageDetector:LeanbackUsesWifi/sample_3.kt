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
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.MANIFEST_SCOPE
        )

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
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("uses-feature")

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttribute("android:name")
        if (name != "android.hardware.wifi") return

        val required = element.getAttribute("android:required")
        if (required == "false") return

        context.report(
            ISSUE,
            context.getLocation(element),
            "WiFi is not required for Android TV. Set `android:required=\"false\"` for `android.hardware.wifi`."
        )
    }

    override fun checkMergedProject(context: Context) {
        // Manifest scanning is handled via XmlScanner
    }
}