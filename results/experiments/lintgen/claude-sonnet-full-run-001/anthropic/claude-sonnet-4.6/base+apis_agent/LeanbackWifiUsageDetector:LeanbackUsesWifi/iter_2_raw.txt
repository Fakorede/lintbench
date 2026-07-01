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
        private const val PERMISSION_ACCESS_WIFI_STATE = "android.permission.ACCESS_WIFI_STATE"
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

        private val WIFI_PERMISSIONS = setOf(
            PERMISSION_ACCESS_WIFI_STATE,
            PERMISSION_CHANGE_WIFI_STATE,
            PERMISSION_CHANGE_WIFI_MULTICAST_STATE
        )

        private const val MESSAGE = "Requiring `android.hardware.wifi` is not recommended for " +
                "Android TV apps; many TV devices connect via Ethernet. Consider setting " +
                "`android:required=\"false\"`."
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE, TAG_USES_PERMISSION)
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        // Parse the entire document to collect all relevant elements
        val root = document.documentElement ?: return

        var hasLeanbackFeature = false
        var wifiFeatureElement: Element? = null
        var wifiFeatureRequiredFalse = false
        val wifiPermissionElements = mutableListOf<Element>()

        // Walk all child elements of the manifest
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) continue

            when (node.tagName) {
                TAG_USES_FEATURE -> {
                    val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    when (name) {
                        SOFTWARE_LEANBACK -> {
                            hasLeanbackFeature = true
                        }
                        HARDWARE_WIFI -> {
                            val requiredAttr = node.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                            if (requiredAttr != null && requiredAttr.value == "false") {
                                wifiFeatureRequiredFalse = true
                            } else {
                                wifiFeatureElement = node
                            }
                        }
                    }
                }
                TAG_USES_PERMISSION -> {
                    val name = node.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name in WIFI_PERMISSIONS) {
                        wifiPermissionElements.add(node)
                    }
                }
            }
        }

        if (!hasLeanbackFeature) return

        // Report wifi feature element if required is not false
        val featureElem = wifiFeatureElement
        if (featureElem != null && !wifiFeatureRequiredFalse) {
            val fix = fix()
                .set(ANDROID_URI, ATTR_REQUIRED, "false")
                .build()

            context.report(
                issue = ISSUE,
                scope = featureElem,
                location = context.getNameLocation(featureElem),
                message = MESSAGE,
                quickfixData = fix
            )
        }

        // If there's no explicit wifi feature declaration (required=true or required=false),
        // wifi permissions imply wifi is required
        if (featureElem == null && !wifiFeatureRequiredFalse) {
            for (permElem in wifiPermissionElements) {
                context.report(
                    issue = ISSUE,
                    scope = permElem,
                    location = context.getNameLocation(permElem),
                    message = MESSAGE
                )
            }
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // All processing is done in visitDocument
    }
}