package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.VALUE_FALSE
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

    private var hasLeanbackFeature = false
    private var hasLeanbackLauncher = false
    private var wifiFeatureElement: Element? = null

    override fun beforeCheckFile(context: XmlContext) {
        hasLeanbackFeature = false
        hasLeanbackLauncher = false
        wifiFeatureElement = null
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE, "category")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (element.tagName == TAG_USES_FEATURE) {
            if (name == "android.software.leanback") {
                hasLeanbackFeature = true
            } else if (name == "android.hardware.wifi") {
                val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                val isRequired = requiredAttr == null || requiredAttr.value != VALUE_FALSE
                if (isRequired) {
                    wifiFeatureElement = element
                }
            }
        } else if (element.tagName == "category") {
            if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                hasLeanbackLauncher = true
            }
        }
    }

    override fun afterCheckFile(context: XmlContext) {
        val element = wifiFeatureElement
        if ((hasLeanbackFeature || hasLeanbackLauncher) && element != null) {
            val fix = fix()
                .name("Set android:required=\"false\"")
                .set(ANDROID_URI, ATTR_REQUIRED, VALUE_FALSE)
                .autoFix()
                .build()

            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Using `android.hardware.wifi` on TV is not required and may prevent the app from being installed on devices without WiFi. Please set `android:required=\"false\"`.",
                fix
            )
        }
    }

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
    }
}