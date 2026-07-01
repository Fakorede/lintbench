package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.EnumSet

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun getApplicableFiles(): EnumSet<Scope> = Scope.MANIFEST_SCOPE

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        val usesFeatures = root.getElementsByTagName("uses-feature")
        var declaresLeanback = false
        var wifiElement: Element? = null
        var wifiRequired = true

        val categories = root.getElementsByTagName("category")
        var hasLeanbackLauncher = false
        for (i in 0 until categories.length) {
            val category = categories.item(i) as Element
            val name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                hasLeanbackLauncher = true
                break
            }
        }

        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as Element
            val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == "android.software.leanback") {
                declaresLeanback = true
            } else if (name == "android.hardware.wifi") {
                wifiElement = element
                val requiredAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED)
                if (requiredAttr != null && requiredAttr.value == "false") {
                    wifiRequired = false
                }
            }
        }

        val isLeanbackApp = declaresLeanback || hasLeanbackLauncher

        if (isLeanbackApp && wifiElement != null && wifiRequired) {
            val location = context.getNameLocation(wifiElement)
            context.report(
                ISSUE,
                wifiElement,
                location,
                "Using `android.hardware.wifi` without `android:required=\"false\"` is not recommended for Android TV."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via \
                alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to \
                connect to the internet, please modify your Manifest to contain: \
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
            """.trimIndent(),
            category = Category.COMPLIANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}