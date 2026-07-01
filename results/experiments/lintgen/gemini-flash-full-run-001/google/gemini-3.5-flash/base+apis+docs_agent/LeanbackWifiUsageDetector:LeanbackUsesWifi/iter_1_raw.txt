package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
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
import java.util.EnumSet

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun getApplicableFiles(): EnumSet<Scope> = Scope.MANIFEST_SCOPE

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!isLeanbackApp(context)) return
        if (isWifiOptional(context)) return

        val root = document.documentElement ?: return

        // Find wifi feature in current document
        var wifiFeature: Element? = null
        val usesFeatures = root.getElementsByTagName("uses-feature")
        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as Element
            val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == "android.hardware.wifi") {
                wifiFeature = element
                break
            }
        }

        if (wifiFeature != null) {
            val location = context.getNameLocation(wifiFeature)
            context.report(
                ISSUE,
                wifiFeature,
                location,
                "Using `android.hardware.wifi` without `android:required=\"false\"` is not recommended for Android TV."
            )
        } else {
            // Find wifi permissions in current document
            val permissionTags = listOf("uses-permission", "uses-permission-sdk-23")
            for (tag in permissionTags) {
                val elements = root.getElementsByTagName(tag)
                for (i in 0 until elements.length) {
                    val element = elements.item(i) as Element
                    val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    if (name == "android.permission.ACCESS_WIFI_STATE" ||
                        name == "android.permission.CHANGE_WIFI_STATE" ||
                        name == "android.permission.CHANGE_WIFI_MULTICAST_STATE"
                    ) {
                        val location = context.getNameLocation(element)
                        context.report(
                            ISSUE,
                            element,
                            location,
                            "Using `android.hardware.wifi` without `android:required=\"false\"` is not recommended for Android TV."
                        )
                    }
                }
            }
        }
    }

    private fun isLeanbackApp(context: XmlContext): Boolean {
        val projects = listOf(context.mainProject) + context.mainProject.allLibraries
        for (project in projects) {
            for (manifestFile in project.manifestFiles) {
                try {
                    val document = context.client.getXmlDocument(manifestFile) ?: continue
                    val root = document.documentElement ?: continue

                    // Check for leanback feature
                    val usesFeatures = root.getElementsByTagName("uses-feature")
                    for (i in 0 until usesFeatures.length) {
                        val element = usesFeatures.item(i) as Element
                        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        if (name == "android.software.leanback") {
                            return true
                        }
                    }

                    // Check for leanback launcher
                    val categories = root.getElementsByTagName("category")
                    for (i in 0 until categories.length) {
                        val category = categories.item(i) as Element
                        val name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                            return true
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        return false
    }

    private fun isWifiOptional(context: XmlContext): Boolean {
        val projects = listOf(context.mainProject) + context.mainProject.allLibraries
        for (project in projects) {
            for (manifestFile in project.manifestFiles) {
                try {
                    val document = context.client.getXmlDocument(manifestFile) ?: continue
                    val root = document.documentElement ?: continue
                    val usesFeatures = root.getElementsByTagName("uses-feature")
                    for (i in 0 until usesFeatures.length) {
                        val element = usesFeatures.item(i) as Element
                        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        if (name == "android.hardware.wifi") {
                            val requiredAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED)
                            if (requiredAttr != null && requiredAttr.value == "false") {
                                return true
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        return false
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