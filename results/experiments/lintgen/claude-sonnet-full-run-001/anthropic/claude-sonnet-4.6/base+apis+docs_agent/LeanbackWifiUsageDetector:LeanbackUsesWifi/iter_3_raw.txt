package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.TAG_USES_PERMISSION
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

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"
        private const val SOFTWARE_LEANBACK = "android.software.leanback"
        private const val PERMISSION_CHANGE_WIFI_STATE = "android.permission.CHANGE_WIFI_STATE"
        private const val PERMISSION_CHANGE_WIFI_MULTICAST_STATE = "android.permission.CHANGE_WIFI_MULTICAST_STATE"

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

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        var hasLeanback = false
        val wifiFeatureElements = mutableListOf<Element>()
        val wifiPermissionElements = mutableListOf<Element>()

        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            val featureName = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
            when (child.tagName) {
                TAG_USES_FEATURE -> {
                    if (featureName == SOFTWARE_LEANBACK) {
                        hasLeanback = true
                    } else if (featureName == HARDWARE_WIFI) {
                        val requiredAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                        if (requiredAttr == null || requiredAttr.value != "false") {
                            wifiFeatureElements.add(child)
                        }
                    }
                }
                TAG_USES_PERMISSION -> {
                    if (featureName == PERMISSION_CHANGE_WIFI_STATE ||
                        featureName == PERMISSION_CHANGE_WIFI_MULTICAST_STATE) {
                        wifiPermissionElements.add(child)
                    }
                }
            }
        }

        if (!hasLeanback) return

        for (element in wifiFeatureElements) {
            val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
            val location = if (requiredAttr != null) {
                context.getValueLocation(requiredAttr)
            } else {
                context.getElementLocation(element)
            }
            context.report(
                issue = ISSUE,
                location = location,
                message = "Requiring `android.hardware.wifi` is not recommended for Android TV " +
                    "apps as many TV devices connect via Ethernet. Consider setting " +
                    "`android:required=\"false\"` unless your app specifically requires WiFi."
            )
        }

        for (element in wifiPermissionElements) {
            val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
            val location = if (nameAttr != null) {
                context.getValueLocation(nameAttr)
            } else {
                context.getElementLocation(element)
            }
            context.report(
                issue = ISSUE,
                location = location,
                message = "Using `${element.getAttributeNS(ANDROID_URI, ATTR_NAME)}` is not " +
                    "recommended for Android TV apps as many TV devices connect via Ethernet."
            )
        }
    }
}