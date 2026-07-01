package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val project = context.mainProject
        val mergedManifest = project.mergedManifest ?: return
        val root = mergedManifest.documentElement ?: return

        var usesLeanback = false
        val usesFeatures = root.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.software.leanback") {
                usesLeanback = true
                break
            }
        }

        if (!usesLeanback) {
            return
        }

        for (i in 0 until usesFeatures.length) {
            val element = usesFeatures.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == "android.hardware.wifi") {
                val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                val required = requiredAttr == null || requiredAttr.value != "false"
                if (required) {
                    val manifestFile = project.manifestFiles.firstOrNull() ?: return
                    val location = context.client.xmlParser.getLocation(manifestFile, element)
                    context.report(
                        ISSUE,
                        location,
                        "Using `android.hardware.wifi` on TV. WiFi is not required for Android TV and many devices connect to the internet via alternative methods e.g. Ethernet. Please modify your Manifest to contain: `<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`"
                    )
                }
            }
        }
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"
        private const val ATTR_REQUIRED = "required"
        private const val TAG_USES_FEATURE = "uses-feature"

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