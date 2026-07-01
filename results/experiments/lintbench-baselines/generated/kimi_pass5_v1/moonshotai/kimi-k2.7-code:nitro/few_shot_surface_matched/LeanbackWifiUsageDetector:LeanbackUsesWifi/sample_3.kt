package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

  private val wifiFeatures = mutableListOf<Pair<XmlContext, org.w3c.dom.Element>>()

  override fun getApplicableElements(): Collection<String> = listOf("uses-feature")

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (element.getAttributeNS(ANDROID_URI, "name") != "android.hardware.wifi") {
      return
    }
    wifiFeatures.add(context to element)
  }

  override fun checkMergedProject(context: Context) {
    if (!isTvApp(context)) {
      wifiFeatures.clear()
      return
    }

    for ((xmlContext, element) in wifiFeatures) {
      val required = element.getAttributeNS(ANDROID_URI, "required")
      if (required != "false") {
        xmlContext.report(
          ISSUE,
          element,
          xmlContext.getLocation(element),
          "Using android.hardware.wifi on TV. WiFi is not required for Android TV; " +
            "if your app only needs to connect to the internet, set " +
            "android:required=\"false\"."
        )
      }
    }

    wifiFeatures.clear()
  }

  private fun isTvApp(context: Context): Boolean {
    val document = context.mainProject.mergedManifest ?: return false
    val features = document.getElementsByTagName("uses-feature")
    for (i in 0 until features.length) {
      val feature = features.item(i) as? org.w3c.dom.Element ?: continue
      val name = feature.getAttributeNS(ANDROID_URI, "name")
      if (name != "android.software.leanback" && name != "android.hardware.type.television") {
        continue
      }
      val required = feature.getAttributeNS(ANDROID_URI, "required")
      if (required != "false") {
        return true
      }
    }
    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "LeanbackUsesWifi",
      briefDescription = "Using android.hardware.wifi on TV",
      explanation = "WiFi is not required for Android TV and many devices connect to the " +
        "internet via alternative methods such as Ethernet. If your app is not focused " +
        "specifically on WiFi functionality and only wishes to connect to the internet, " +
        "modify your Manifest to contain: " +
        "<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />. " +
        "Un-metered or non-roaming connections can be detected in software using " +
        "NetworkCapabilities#NET_CAPABILITY_NOT_METERED and " +
        "NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.",
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