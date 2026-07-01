package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_USES_FEATURE, SdkConstants.TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isTvProject(context)) {
            return
        }

        when (element.tagName) {
            SdkConstants.TAG_USES_FEATURE -> {
                val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name != WIFI_FEATURE) {
                    return
                }
                val required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED)
                if (required == SdkConstants.VALUE_FALSE) {
                    return
                }
                report(context, element)
            }
            SdkConstants.TAG_USES_PERMISSION -> {
                val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                if (name !in WIFI_PERMISSIONS) {
                    return
                }
                if (hasWifiFeature(context.document) || hasWifiFeature(context.mainProject.manifestDom)) {
                    return
                }
                report(context, element)
            }
        }
    }

    private fun isTvProject(context: XmlContext): Boolean {
        val mainManifest = context.mainProject.manifestDom
        if (mainManifest != null && isTvManifest(mainManifest)) {
            return true
        }
        val currentManifest = context.project.manifestDom
        return currentManifest != null && isTvManifest(currentManifest)
    }

    private fun isTvManifest(document: Document): Boolean {
        val root = document.documentElement ?: return false

        val features = root.getElementsByTagName(SdkConstants.TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            val name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == LEANBACK_FEATURE) {
                return true
            }
        }

        val categories = root.getElementsByTagName(SdkConstants.TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? Element ?: continue
            val name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == LEANBACK_LAUNCHER) {
                return true
            }
        }

        return false
    }

    private fun hasWifiFeature(document: Document?): Boolean {
        document ?: return false
        val root = document.documentElement ?: return false
        val features = root.getElementsByTagName(SdkConstants.TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            val name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == WIFI_FEATURE) {
                return true
            }
        }
        return false
    }

    private fun report(context: XmlContext, element: Element) {
        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "Using `android.hardware.wifi` on TV"
        )
    }

    companion object {
        private const val WIFI_FEATURE = "android.hardware.wifi"
        private const val LEANBACK_FEATURE = "android.software.leanback"
        private const val LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"

        private val WIFI_PERMISSIONS = setOf(
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using `android.hardware.wifi` on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain: `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
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