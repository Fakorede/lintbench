package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.project != context.mainProject) {
            return
        }

        val mainManifest = context.project.manifestFiles.firstOrNull()
        if (mainManifest != null && context.file != mainManifest) {
            return
        }

        var hasLeanbackFeature = false
        var hasLeanbackLauncher = false
        var hasWifiNotRequired = false

        var wifiFeatureElement: Element? = null
        var wifiFeatureFile: File? = null

        val wifiPermissions = mutableListOf<Pair<Element, File>>()

        val manifests = mutableListOf<File>()
        manifests.addAll(context.project.manifestFiles)
        for (library in context.project.allLibraries) {
            manifests.addAll(library.manifestFiles)
        }

        for (file in manifests) {
            val doc = try {
                context.client.xmlParser.parseXml(file)
            } catch (e: Exception) {
                null
            } ?: continue

            val usesFeatures = doc.getElementsByTagName("uses-feature")
            for (i in 0 until usesFeatures.length) {
                val element = usesFeatures.item(i) as Element
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "android.hardware.wifi") {
                    val requiredAttr = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                    if (requiredAttr == "false") {
                        hasWifiNotRequired = true
                    } else {
                        wifiFeatureElement = element
                        wifiFeatureFile = file
                    }
                } else if (name == "android.software.leanback") {
                    hasLeanbackFeature = true
                }
            }

            val categories = doc.getElementsByTagName("category")
            for (i in 0 until categories.length) {
                val element = categories.item(i) as Element
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "android.intent.category.LEANBACK_LAUNCHER") {
                    hasLeanbackLauncher = true
                }
            }

            val usesPermissions = doc.getElementsByTagName("uses-permission")
            for (i in 0 until usesPermissions.length) {
                val element = usesPermissions.item(i) as Element
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == "android.permission.ACCESS_WIFI_STATE" ||
                    name == "android.permission.CHANGE_WIFI_STATE" ||
                    name == "android.permission.CHANGE_WIFI_MULTICAST_STATE"
                ) {
                    wifiPermissions.add(element to file)
                }
            }
        }

        val isLeanback = hasLeanbackFeature || hasLeanbackLauncher
        if (!isLeanback) {
            return
        }

        if (hasWifiNotRequired) {
            return
        }

        if (wifiFeatureElement != null && wifiFeatureFile != null) {
            val location = context.client.xmlParser.getLocation(wifiFeatureFile, wifiFeatureElement)
            context.report(
                ISSUE,
                wifiFeatureElement,
                location,
                "Using `android.hardware.wifi` on TV"
            )
            return
        }

        if (wifiPermissions.isNotEmpty()) {
            val (permissionElement, permissionFile) = wifiPermissions.first()
            val location = context.client.xmlParser.getLocation(permissionFile, permissionElement)
            context.report(
                ISSUE,
                permissionElement,
                location,
                "Using `android.hardware.wifi` on TV"
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