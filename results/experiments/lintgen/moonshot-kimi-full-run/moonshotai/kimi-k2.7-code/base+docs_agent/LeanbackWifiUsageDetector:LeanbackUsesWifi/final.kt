package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    private val tvProjectCache = mutableMapOf<Project, Boolean>()

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_USES_FEATURE, SdkConstants.TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isTvProject(context.mainProject)) {
            return
        }

        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        when (element.tagName) {
            SdkConstants.TAG_USES_FEATURE -> {
                if (name == HARDWARE_WIFI) {
                    val required = element.getAttributeNS(
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_REQUIRED
                    )
                    if (required == SdkConstants.VALUE_FALSE) {
                        return
                    }
                    reportWifiUsage(context, element, name)
                }
            }
            SdkConstants.TAG_USES_PERMISSION -> {
                if (name in WIFI_PERMISSIONS) {
                    reportWifiUsage(context, element, name)
                }
            }
        }
    }

    private fun isTvProject(project: Project): Boolean {
        return tvProjectCache.getOrPut(project) {
            isTvManifest(project) || project.allLibraries.any { isTvManifest(it) }
        }
    }

    private fun isTvManifest(project: Project): Boolean {
        val manifestFiles = project.manifestFiles
        return manifestFiles.any { file ->
            val doc = XmlUtils.parseDocumentSilently(file, true)
            doc?.documentElement?.let { isLeanbackManifest(it) } ?: false
        }
    }

    private fun isLeanbackManifest(manifest: Element): Boolean {
        val features = manifest.getElementsByTagName(SdkConstants.TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            val name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == HARDWARE_TYPE_TELEVISION || name == SOFTWARE_LEANBACK) {
                val required = feature.getAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_REQUIRED
                )
                if (required != SdkConstants.VALUE_FALSE) {
                    return true
                }
            }
        }

        val categories = manifest.getElementsByTagName(SdkConstants.TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? Element ?: continue
            val name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (name == CATEGORY_LEANBACK_LAUNCHER) {
                return true
            }
        }

        return false
    }

    private fun reportWifiUsage(context: XmlContext, element: Element, name: String) {
        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Using `$name` on Android TV; Wi-Fi is not required for Android TV and many devices " +
                "connect to the internet via alternative methods such as Ethernet. If your app is " +
                "not focused specifically on Wi-Fi functionality, consider setting " +
                "`android:required=\"false\"` for the hardware feature or using ConnectivityManager."
        )
    }

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"
        private const val HARDWARE_TYPE_TELEVISION = "android.hardware.type.television"
        private const val SOFTWARE_LEANBACK = "android.software.leanback"
        private const val CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"

        private val WIFI_PERMISSIONS = setOf(
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.ACCESS_WIFI_MULTICAST_STATE"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using Wi-Fi on Android TV",
            explanation = """
                Wi-Fi is not required for Android TV and many devices connect to the internet via
                alternative methods such as Ethernet. If your app is not focused specifically on
                Wi-Fi functionality and only wishes to connect to the internet, please modify your
                Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
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