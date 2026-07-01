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

    /** Whether the manifest declares leanback as a feature (required or not). */
    private var usesLeanback = false

    /** Whether wifi is declared as explicitly required (required="true" or no required attr). */
    private var wifiRequiredElement: Element? = null

    /** Whether wifi is declared with required="false". */
    private var wifiDeclaredNotRequired = false

    override fun beforeCheckFile(context: Context) {
        usesLeanback = false
        wifiRequiredElement = null
        wifiDeclaredNotRequired = false
    }

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_FEATURE)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return

        when (name) {
            "android.software.leanback" -> {
                usesLeanback = true
            }
            HARDWARE_WIFI -> {
                val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                if (requiredAttr != null && requiredAttr.value == "false") {
                    wifiDeclaredNotRequired = true
                } else {
                    // Either required="true" explicitly, or the attribute is absent
                    // (which defaults to required=true per the manifest spec).
                    wifiRequiredElement = element
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        if (!usesLeanback) return
        if (wifiDeclaredNotRequired) return

        val element = wifiRequiredElement ?: return

        val xmlContext = context as? XmlContext ?: return

        xmlContext.report(
            issue = ISSUE,
            location = xmlContext.getNameLocation(element),
            message = "Requiring `android.hardware.wifi` is not recommended for Android TV " +
                "apps; many TV devices connect to the internet via Ethernet. Consider " +
                "setting `android:required=\"false\"`."
        )
    }
}