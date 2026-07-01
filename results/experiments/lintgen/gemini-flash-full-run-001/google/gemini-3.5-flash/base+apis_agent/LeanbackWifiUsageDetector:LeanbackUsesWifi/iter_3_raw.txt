package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
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

    private var hasLeanback = false
    private var hasWifiRequiredFalse = false
    private val wifiFeatureElements = mutableListOf<PendingReport>()
    private val wifiPermissionElements = mutableListOf<PendingReport>()

    private data class PendingReport(
        val context: XmlContext,
        val element: Element,
        val message: String
    )

    override fun beforeCheckEachProject(context: Context) {
        hasLeanback = false
        hasWifiRequiredFalse = false
        wifiFeatureElements.clear()
        wifiPermissionElements.clear()
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        visit(context, root)
    }

    private fun visit(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "uses-feature") {
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.software.leanback") {
                hasLeanback = true
            } else if (name == "android.hardware.wifi") {
                val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                if (required == "false") {
                    hasWifiRequiredFalse = true
                } else {
                    wifiFeatureElements.add(
                        PendingReport(
                            context,
                            element,
                            "Using `android.hardware.wifi` without `android:required=\"false\"` is not recommended for TV devices"
                        )
                    )
                }
            }
        } else if (tagName == "category") {
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                hasLeanback = true
            }
        } else if (tagName == "uses-permission" || tagName == "uses-permission-sdk-23") {
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.permission.ACCESS_WIFI_STATE" ||
                name == "android.permission.CHANGE_WIFI_STATE" ||
                name == "android.permission.CHANGE_WIFI_MULTICAST_STATE"
            ) {
                wifiPermissionElements.add(
                    PendingReport(
                        context,
                        element,
                        "Using `android.hardware.wifi` without `android:required=\"false\"` is not recommended for TV devices"
                    )
                )
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                visit(context, child)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (hasLeanback && !hasWifiRequiredFalse) {
            if (wifiFeatureElements.isNotEmpty()) {
                for (report in wifiFeatureElements) {
                    report.context.report(
                        ISSUE,
                        report.element,
                        report.context.getLocation(report.element),
                        report.message
                    )
                }
            } else {
                for (report in wifiPermissionElements) {
                    report.context.report(
                        ISSUE,
                        report.element,
                        report.context.getLocation(report.element),
                        report.message
                    )
                }
            }
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