package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.VALUE_FALSE
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE, TAG_USES_PERMISSION, "uses-permission-sdk-23")
    }

    private var hasLeanback = false
    private var hasTvHardware = false
    private var declaresWifiFeature = false
    private var wifiRequired = true
    private var wifiFeature: Pair<Element, XmlContext>? = null
    private val wifiPermissions = mutableListOf<Pair<Element, XmlContext>>()

    override fun beforeCheckRootProject(context: Context) {
        hasLeanback = false
        hasTvHardware = false
        declaresWifiFeature = false
        wifiRequired = true
        wifiFeature = null
        wifiPermissions.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        when (element.tagName) {
            TAG_USES_FEATURE -> {
                if (name == "android.software.leanback") {
                    hasLeanback = true
                } else if (name == "android.hardware.type.television") {
                    hasTvHardware = true
                } else if (name == "android.hardware.wifi") {
                    declaresWifiFeature = true
                    wifiFeature = Pair(element, context)
                    val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                    if (required == VALUE_FALSE) {
                        wifiRequired = false
                    }
                }
            }
            TAG_USES_PERMISSION, "uses-permission-sdk-23" -> {
                if (name == "android.permission.ACCESS_WIFI_STATE" ||
                    name == "android.permission.CHANGE_WIFI_STATE" ||
                    name == "android.permission.CHANGE_WIFI_MULTICAST_STATE"
                ) {
                    wifiPermissions.add(Pair(element, context))
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!hasLeanback && !hasTvHardware) {
            return
        }

        if (declaresWifiFeature) {
            if (wifiRequired) {
                val feature = wifiFeature
                if (feature != null) {
                    val (element, xmlContext) = feature
                    xmlContext.report(
                        ISSUE,
                        element,
                        xmlContext.getNameLocation(element),
                        "Expecting `android:required=\"false\"` for `android.hardware.wifi` when running on TV"
                    )
                }
            }
        } else {
            for ((permission, xmlContext) in wifiPermissions) {
                xmlContext.report(
                    ISSUE,
                    permission,
                    xmlContext.getNameLocation(permission),
                    "Expecting `android:required=\"false\"` for `android.hardware.wifi` when running on TV"
                )
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