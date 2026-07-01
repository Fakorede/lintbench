package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : ResourceXmlDetector() {

    private var isTvApp = false
    private var wifiElement: Element? = null
    private var wifiRequiredAttr: Attr? = null

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
            implementation = Implementation(LeanbackWifiUsageDetector::class.java, Scope.MANIFEST)
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_USES_FEATURE)

    override fun beforeCheckFile(context: XmlContext) {
        isTvApp = false
        wifiElement = null
        wifiRequiredAttr = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)

        if (name == "android.hardware.wifi" && required != "false") {
            wifiElement = element
            wifiRequiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
        } else if ((name == "android.software.leanback" || name == "android.hardware.type.television") && required != "false") {
            isTvApp = true
        }
    }

    override fun afterCheckFile(context: XmlContext) {
        if (isTvApp && wifiElement != null) {
            val location = context.getLocation(wifiRequiredAttr ?: wifiElement!!)
            val fix = fix()
                .set(ANDROID_URI, ATTR_REQUIRED, "false")
                .build()

            context.report(
                ISSUE,
                location,
                "WiFi is not required for Android TV. Set `android:required=\"false\"` or remove this feature if not strictly needed.",
                fix
            )
        }
    }

    override fun getIssues(): List<Issue> = listOf(ISSUE)
}