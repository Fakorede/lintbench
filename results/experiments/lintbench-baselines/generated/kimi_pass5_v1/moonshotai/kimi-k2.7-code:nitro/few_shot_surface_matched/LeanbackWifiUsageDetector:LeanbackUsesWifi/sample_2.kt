package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_USES_FEATURE)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    // Analysis is performed on the merged manifest in checkMergedProject.
  }

  override fun checkMergedProject(context: Context) {
    val manifest = context.project.mergedManifest ?: context.project.manifest ?: return
    if (!manifest.exists()) {
      return
    }

    val document = context.client.xmlParser.parseFile(manifest) ?: return
    val features = document.getElementsByTagName(TAG_USES_FEATURE)

    var isTv = false
    var wifiElement: org.w3c.dom.Element? = null

    for (i in 0 until features.length) {
      val element = features.item(i) as? org.w3c.dom.Element ?: continue
      val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
      when (name) {
        FEATURE_WIFI -> {
          val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
          if (required != VALUE_FALSE) {
            wifiElement = element
          }
        }
        FEATURE_LEANBACK, FEATURE_TELEVISION -> isTv = true
      }
    }

    if (isTv && wifiElement != null) {
      val location = if (context is XmlContext) {
        context.getLocation(wifiElement)
      } else {
        context.getLocation(manifest)
      }
      context.report(
        ISSUE,
        location,
        "Using android.hardware.wifi on TV. WiFi is not required for Android TV and many " +
          "devices connect to the internet via alternative methods e.g. Ethernet."
      )
    }
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val ATTR_NAME = "name"
    private const val ATTR_REQUIRED = "required"
    private const val TAG_USES_FEATURE = "uses-feature"
    private const val FEATURE_WIFI = "android.hardware.wifi"
    private const val FEATURE_LEANBACK = "android.software.leanback"
    private const val FEATURE_TELEVISION = "android.hardware.type.television"
    private const val VALUE_FALSE = "false"

    @JvmField
    val ISSUE = Issue.create(
      id = "LeanbackUsesWifi",
      briefDescription = "Using android.hardware.wifi on TV",
      explanation = "WiFi is not required for Android TV and many devices connect to the internet " +
        "via alternative methods e.g. Ethernet. If your app is not focused specifically on WiFi " +
        "functionality and only wishes to connect to the internet, please modify your Manifest to " +
        "contain: `<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`. " +
        "Un-metered or non-roaming connections can be detected in software using " +
        "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
        "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.",
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