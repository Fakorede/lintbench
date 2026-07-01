package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val ATTR_NAME = "name"
private const val ATTR_PACKAGE = "package"
private const val ATTR_VALUE = "value"
private const val CATEGORY_WEARABLE_CONFIGURATION = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
private const val META_DATA_NAME = "wearableConfigurationAction"
private const val TAG_ACTION = "action"
private const val TAG_ACTIVITY = "activity"
private const val TAG_CATEGORY = "category"
private const val TAG_INTENT_FILTER = "intent-filter"
private const val TAG_MANIFEST = "manifest"
private const val TAG_META_DATA = "meta-data"
private const val TAG_SERVICE = "service"

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return emptyList()
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    // Not used; the check is performed on the merged manifest.
  }

  override fun checkMergedProject(context: Context) {
    val document = context.mainProject.mergedManifest ?: return
    val manifest = document.documentElement ?: return

    val needsWearableCategory = context.mainProject.minSdkVersion.apiLevel < 30

    val services = manifest.getElementsByTagName(TAG_SERVICE)
    for (i in 0 until services.length) {
      val service = services.item(i) as? org.w3c.dom.Element ?: continue
      val metaDataNodes = service.getElementsByTagName(TAG_META_DATA)
      for (j in 0 until metaDataNodes.length) {
        val metaData = metaDataNodes.item(j) as? org.w3c.dom.Element ?: continue
        if (metaData.getAttributeNS(ANDROID_URI, ATTR_NAME) != META_DATA_NAME) {
          continue
        }
        val action = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
        if (action.isBlank()) {
          continue
        }
        if (!hasMatchingActivity(manifest, action, needsWearableCategory)) {
          val message = "The wearableConfigurationAction '$action' must match an activity " +
              "with an intent-filter action '$action'. For minSdkVersion < 30, the intent filter " +
              "must also include the category '$CATEGORY_WEARABLE_CONFIGURATION'."
          if (context is XmlContext) {
            context.report(ISSUE, metaData, context.getLocation(metaData), message)
          } else {
            context.report(ISSUE, Location.create(context.file), message)
          }
        }
      }
    }
  }

  private fun hasMatchingActivity(
    manifest: org.w3c.dom.Element,
    action: String,
    needsWearableCategory: Boolean
  ): Boolean {
    val activities = manifest.getElementsByTagName(TAG_ACTIVITY)
    for (i in 0 until activities.length) {
      val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
      val filters = activity.getElementsByTagName(TAG_INTENT_FILTER)
      for (j in 0 until filters.length) {
        val filter = filters.item(j) as? org.w3c.dom.Element ?: continue
        var hasAction = false
        var hasCategory = false
        val children = filter.childNodes
        for (k in 0 until children.length) {
          val node = children.item(k) as? org.w3c.dom.Element ?: continue
          when (node.tagName) {
            TAG_ACTION -> {
              if (node.getAttributeNS(ANDROID_URI, ATTR_NAME) == action) {
                hasAction = true
              }
            }
            TAG_CATEGORY -> {
              if (node.getAttributeNS(ANDROID_URI, ATTR_NAME) == CATEGORY_WEARABLE_CONFIGURATION) {
                hasCategory = true
              }
            }
          }
        }
        if (hasAction && (!needsWearableCategory || hasCategory)) {
          return true
        }
      }
    }
    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "WearableConfigurationAction",
      briefDescription = "Wearable configuration action must match an activity",
      explanation = "When a watch face service declares a `wearableConfigurationAction` metadata value, " +
        "there must be a corresponding activity in the same package with an intent-filter action " +
        "matching that value. For `minSdkVersion` less than 30, the intent filter must also include " +
        "the `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        WearableConfigurationActionDetector::class.java,
        Scope.MANIFEST_SCOPE
      )
    )
  }
}