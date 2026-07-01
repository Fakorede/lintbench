package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_USES_FEATURE)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
    if (nameAttr.value != "android.hardware.wifi") return

    val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
    val isRequired = requiredAttr == null || requiredAttr.value.toBoolean()
    if (!isRequired) return

    val root = element.ownerDocument.documentElement
    var isTvApp = false
    var child = root.firstChild
    while (child != null) {
      if (child is Element && child.tagName == TAG_USES_FEATURE) {
        val featureName = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val featureRequired = child.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
        val isFeatureRequired = featureRequired.isEmpty() || featureRequired.toBoolean()
        if (isFeatureRequired && (featureName == "android.software.leanback" || featureName == "android.hardware.type.television")) {
          isTvApp = true
          break
        }
      }
      child = child.nextSibling
    }

    if (!isTvApp) return

    context.report(
      ISSUE,
      requiredAttr ?: nameAttr,
      context.getValueLocation(requiredAttr ?: nameAttr),
      "WiFi is not required for Android TV and many devices connect to the internet via " +
      "alternative methods e.g. Ethernet. If your app is not focused specifically on WiFi " +
      "functionality and only wishes to connect to the internet, please modify your Manifest " +
      "to contain: `<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`"
    )
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "LeanbackUsesWifi",
      briefDescription = "Using android.hardware.wifi on TV",
      explanation = "WiFi is not required for Android TV and many devices connect to the internet via " +
        "alternative methods e.g. Ethernet.\n\n" +
        "If your app is not focused specifically on WiFi functionality and only wishes to " +
        "connect to the internet, please modify your Manifest to contain: " +
        "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`\n\n" +
        "Un-metered or non-roaming connections can be detected in software using " +
        "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
        "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(LeanbackWifiUsageDetector::class.java, Scope.MANIFEST_SCOPE)
    )
  }
}