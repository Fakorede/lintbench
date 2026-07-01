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
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): List<String> = emptyList()

  override fun visitElement(context: XmlContext, element: Element) {
    // All analysis is performed on the merged manifest in checkMergedProject.
  }

  override fun checkMergedProject(context: Context) {
    val manifest = context.mainProject.mergedManifest ?: return
    val root = manifest.documentElement ?: return
    if (root.tagName != MANIFEST) return

    val packageName = root.getAttribute(ATTR_PACKAGE)
    val minSdkVersion = context.mainProject.minSdk

    val services = root.getElementsByTagName(SERVICE)
    val activities = root.getElementsByTagName(ACTIVITY)

    for (i in 0 until services.length) {
      val service = services.item(i) as? Element ?: continue
      val metaData = service.findMetaData(WEARABLE_CONFIG_ACTION, WATCH_FACE_EDITOR) ?: continue

      val matches = mutableListOf<String>()
      for (j in 0 until activities.length) {
        val activity = activities.item(j) as? Element ?: continue
        if (!isInSamePackage(activity, packageName)) continue
        if (activity.handlesAction(WATCH_FACE_EDITOR, minSdkVersion)) {
          matches.add(activity.getAttributeNS(ANDROID_URI, ATTR_NAME))
        }
      }

      if (matches.size > 1) {
        val message = "Duplicate watch face configuration activities found for action " +
          "$WATCH_FACE_EDITOR: ${matches.joinToString(", ")}"
        context.report(ISSUE, context.getLocation(metaData), message)
      }
    }
  }

  private fun Element.findMetaData(name: String, value: String): Element? {
    for (i in 0 until childNodes.length) {
      val node = childNodes.item(i)
      if (node.nodeType != Node.ELEMENT_NODE || node.tagName != META_DATA) continue
      val element = node as Element
      if (element.getAttributeNS(ANDROID_URI, ATTR_NAME) == name &&
        element.getAttributeNS(ANDROID_URI, ATTR_VALUE) == value) {
        return element
      }
    }
    return null
  }

  private fun Element.handlesAction(action: String, minSdk: Int): Boolean {
    for (i in 0 until childNodes.length) {
      val node = childNodes.item(i)
      if (node.nodeType != Node.ELEMENT_NODE || node.tagName != INTENT_FILTER) continue
      val intentFilter = node as Element
      var hasAction = false
      var hasCategory = false
      for (j in 0 until intentFilter.childNodes.length) {
        val child = intentFilter.childNodes.item(j)
        if (child.nodeType != Node.ELEMENT_NODE) continue
        val element = child as Element
        when (element.tagName) {
          ACTION -> if (element.getAttributeNS(ANDROID_URI, ATTR_NAME) == action) hasAction = true
          CATEGORY -> if (element.getAttributeNS(ANDROID_URI, ATTR_NAME) ==
            WEARABLE_CONFIGURATION_CATEGORY) hasCategory = true
        }
      }
      if (hasAction && (minSdk >= 30 || hasCategory)) return true
    }
    return false
  }

  private fun isInSamePackage(activity: Element, packageName: String): Boolean {
    if (packageName.isEmpty()) return true
    val name = activity.getAttributeNS(ANDROID_URI, ATTR_NAME)
    return name.startsWith(".") || name.startsWith("$packageName.")
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val MANIFEST = "manifest"
    private const val ATTR_PACKAGE = "package"
    private const val SERVICE = "service"
    private const val ACTIVITY = "activity"
    private const val META_DATA = "meta-data"
    private const val INTENT_FILTER = "intent-filter"
    private const val ACTION = "action"
    private const val CATEGORY = "category"
    private const val ATTR_NAME = "name"
    private const val ATTR_VALUE = "value"
    private const val WEARABLE_CONFIG_ACTION =
      "com.google.android.wearable.watchface.wearableConfigurationAction"
    private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
    private const val WEARABLE_CONFIGURATION_CATEGORY =
      "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

    @JvmField
    val ISSUE = Issue.create(
      id = "WearableActionDuplicate",
      briefDescription = "Duplicate watch face configuration activities",
      explanation = "If a watch face service defines the wearableConfigurationAction metadata " +
        "with value WATCH_FACE_EDITOR, there must be exactly one activity in the same package " +
        "with an intent filter for that action. When minSdkVersion is less than 30, the activity " +
        "must also declare the com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION " +
        "category.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      moreInfo = "https://developer.android.com/training/wearables/watch-faces/configuration",
      implementation = Implementation(
        WearableConfigurationActionDetector::class.java,
        EnumSet.of(Scope.MERGED_MANIFEST)
      )
    )
  }
}