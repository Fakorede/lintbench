package com.android.tools.lint.checks

import com.android.tools.lint.client.api.ClassReferences
import com.android.tools.lint.detector.api.*

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

  override fun checkMergedProject(
    context: Context,
    project: Project,
    main: Project,
    referenceClient: ClassReferences?
  ) {
    if (project != main) {
      return
    }

    val manifest = context.getMergedManifest(main) ?: return
    val root = manifest.documentElement ?: return

    if (!isTvApp(root)) {
      return
    }

    val features = root.getElementsByTagName("uses-feature")
    for (i in 0 until features.length) {
      val feature = features.item(i) as? org.w3c.dom.Element ?: continue
      if (feature.getAttributeNS(ANDROID_NS, "name") != WIFI) {
        continue
      }
      if (isRequired(feature)) {
        context.report(
          ISSUE,
          feature,
          context.getLocation(feature),
          "Using android.hardware.wifi on TV; WiFi is not required for Android TV. " +
                  "If your app only needs to connect to the internet, set " +
                  "android:required=\"false\" on this <uses-feature> element."
        )
      }
    }
  }

  private fun isTvApp(root: org.w3c.dom.Element): Boolean {
    val features = root.getElementsByTagName("uses-feature")
    for (i in 0 until features.length) {
      val feature = features.item(i) as? org.w3c.dom.Element ?: continue
      val name = feature.getAttributeNS(ANDROID_NS, "name")
      if (name == TV || name == LEANBACK) {
        if (isRequired(feature)) {
          return true
        }
      }
    }

    val categories = root.getElementsByTagName("category")
    for (i in 0 until categories.length) {
      val category = categories.item(i) as? org.w3c.dom.Element ?: continue
      if (category.getAttributeNS(ANDROID_NS, "name") == LEANBACK_LAUNCHER) {
        return true
      }
    }

    return false
  }

  private fun isRequired(element: org.w3c.dom.Element): Boolean {
    val required = element.getAttributeNS(ANDROID_NS, "required")
    return when {
      required.isEmpty() -> true
      required.equals("true", ignoreCase = true) -> true
      required == "1" -> true
      else -> false
    }
  }

  companion object {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val WIFI = "android.hardware.wifi"
    private const val TV = "android.hardware.type.television"
    private const val LEANBACK = "android.software.leanback"
    private const val LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"

    @JvmField
    val ISSUE = Issue.create(
      id = "LeanbackUsesWifi",
      briefDescription = "Using android.hardware.wifi on TV",
      explanation = "WiFi is not required for Android TV and many devices connect to the internet via " +
              "alternative methods e.g. Ethernet.\n\n" +
              "If your app is not focused specifically on WiFi functionality and only wishes to " +
              "connect to the internet, modify your Manifest to contain:\n" +
              "  <uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />\n\n" +
              "Un-metered or non-roaming connections can be detected in software using " +
              "NetworkCapabilities#NET_CAPABILITY_NOT_METERED and " +
              "NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(LeanbackWifiUsageDetector::class.java, Scope.MANIFEST_SCOPE)
    )
  }
}