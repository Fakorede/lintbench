package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

  override fun checkMergedProject(
    context: Context,
    project: Project,
    main: Project,
    current: Project?
  ) {
    val mainProject = context.mainProject
    if (project != mainProject) {
      return
    }

    val mergedManifest = mainProject.mergedManifest ?: return
    if (!mergedManifest.isFile) {
      return
    }

    val document = context.client.xmlParser.parseXml(mergedManifest, true) ?: return
    val root = document.documentElement ?: return
    if (root.tagName != "manifest") {
      return
    }

    if (!isLeanbackApp(root)) {
      return
    }

    val features = root.getElementsByTagName("uses-feature")
    for (i in 0 until features.length) {
      val feature = features.item(i) as? org.w3c.dom.Element ?: continue
      if (feature.getAttributeNS(ANDROID_URI, "name") != WIFI_FEATURE) {
        continue
      }

      val required = feature.getAttributeNS(ANDROID_URI, "required")
      if (required.equals("false", ignoreCase = true)) {
        continue
      }

      context.report(
        ISSUE,
        Location.create(mergedManifest),
        "Using android.hardware.wifi on TV. WiFi is not required for Android TV; " +
          "if your app only needs to connect to the internet, declare the feature with " +
          "android:required=\"false\"."
      )
      return
    }
  }

  private fun isLeanbackApp(root: org.w3c.dom.Element): Boolean {
    val intentFilters = root.getElementsByTagName("intent-filter")
    for (i in 0 until intentFilters.length) {
      val filter = intentFilters.item(i) as? org.w3c.dom.Element ?: continue
      val parent = filter.parentNode as? org.w3c.dom.Element ?: continue
      val tag = parent.tagName
      if (tag != "activity" && tag != "activity-alias") {
        continue
      }

      var hasMain = false
      var hasLeanback = false

      val actions = filter.getElementsByTagName("action")
      for (j in 0 until actions.length) {
        val action = actions.item(j) as? org.w3c.dom.Element ?: continue
        if (action.getAttributeNS(ANDROID_URI, "name") == MAIN_ACTION) {
          hasMain = true
          break
        }
      }

      val categories = filter.getElementsByTagName("category")
      for (j in 0 until categories.length) {
        val category = categories.item(j) as? org.w3c.dom.Element ?: continue
        if (category.getAttributeNS(ANDROID_URI, "name") == LEANBACK_CATEGORY) {
          hasLeanback = true
          break
        }
      }

      if (hasMain && hasLeanback) {
        return true
      }
    }
    return false
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val WIFI_FEATURE = "android.hardware.wifi"
    private const val MAIN_ACTION = "android.intent.action.MAIN"
    private const val LEANBACK_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER"

    @JvmField
    val ISSUE = Issue.create(
      id = "LeanbackUsesWifi",
      briefDescription = "Using Wi-Fi on TV",
      explanation = """
        WiFi is not required for Android TV and many devices connect to the internet via
        alternative methods e.g. Ethernet.

        If your app is not focused specifically on WiFi functionality and only wishes to
        connect to the internet, please modify your Manifest to contain:
        `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

        Un-metered or non-roaming connections can be detected in software using
        `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and
        `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(LeanbackWifiUsageDetector::class.java, Scope.MANIFEST_SCOPE)
    )
  }
}