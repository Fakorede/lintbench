package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf("uses-feature")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.getAttributeNS(ANDROID_URI, "name") != "android.hardware.wifi") {
            return
        }

        if (element.getAttributeNS(ANDROID_URI, "required") == "false") {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Using `android.hardware.wifi` as required on TV; declare it with `android:required=\"false\"` unless the app specifically requires WiFi."
        )
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via
                alternative methods such as Ethernet. If your app is not focused specifically on
                WiFi functionality and only wishes to connect to the internet, declare the WiFi
                feature as not required:

                <uses-feature android:name="android.hardware.wifi" android:required="false" />

                Un-metered or non-roaming connections can be detected in software using
                NetworkCapabilities#NET_CAPABILITY_NOT_METERED and
                NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.
            """.trimIndent(),
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