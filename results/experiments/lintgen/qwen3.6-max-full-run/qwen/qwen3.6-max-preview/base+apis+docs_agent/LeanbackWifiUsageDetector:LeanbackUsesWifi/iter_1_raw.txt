package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf("uses-feature")

    override fun visitElement(context: XmlContext, element: Element) {
        val ns = "http://schemas.android.com/apk/res/android"
        val name = element.getAttributeNS(ns, "name")
        if (name != "android.hardware.wifi") return

        val required = element.getAttributeNS(ns, "required")
        if (required == "false") return

        context.report(
            ISSUE,
            context.getLocation(element),
            "WiFi is not required for Android TV. Set android:required=\"false\" for android.hardware.wifi."
        )
    }

    companion object {
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
    }
}