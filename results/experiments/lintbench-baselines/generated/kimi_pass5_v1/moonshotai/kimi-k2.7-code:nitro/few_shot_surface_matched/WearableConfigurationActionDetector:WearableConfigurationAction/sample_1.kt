package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

private typealias Element = org.w3c.dom.Element

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val ATTR_NAME = "name"
private const val ATTR_VALUE = "value"
private const val TAG_APPLICATION = "application"
private const val TAG_SERVICE = "service"
private const val TAG_ACTIVITY = "activity"
private const val TAG_INTENT_FILTER = "intent-filter"
private const val TAG_ACTION = "action"
private const val TAG_CATEGORY = "category"
private const val TAG_META_DATA = "meta-data"
private const val WATCH_FACE_SERVICE_ACTION = "android.service.watchface.WatchFaceService"
private const val WEARABLE_CONFIGURATION_ACTION = "com.google.android.wearable.watchface.wearableConfigurationAction"
private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
private const val MIN_SDK_FOR_OPTIONAL_CATEGORY = 30

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> = emptyList()

  override fun checkMergedProject(context: Context) {
    val project = context.mainProject
    val manifest = project.mergedManifest ?: return
    val root = manifest.documentElement ?: return
    val application = root.getElementsByTagName(TAG_APPLICATION).item(0) as? Element ?: return

    val requiresCategory = !project.minSdkVersion.isAtLeast(MIN_SDK_FOR_OPTIONAL_CATEGORY)
    val hasConfigurationActivity = hasConfigurationActivity(application, requiresCategory)

    val services = application.getElementsByTagName(TAG_SERVICE)
    for (i in 0 until services.length) {
      val service = services.item(i) as? Element ?: continue
      if (!isWatchFaceService(service)) continue

      val metaDataNodes = service.getElementsByTagName(TAG_META_DATA)
      for (j in 0 until metaDataNodes.length) {
        val metaData = metaDataNodes.item(j) as? Element ?: continue
        if (metaData.getAttributeNS(ANDROID_URI, ATTR_NAME) != WEARABLE_CONFIGURATION_ACTION) continue
        if (metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE) != WATCH_FACE_EDITOR) continue

        if (!hasConfigurationActivity) {
          val message = buildString {
            append("Watch face service declares wearableConfigurationAction WATCH_FACE_EDITOR, ")
            append("but no activity in the manifest handles the WATCH_FACE_EDITOR action")
            if (requiresCategory) {
              append(" with category ")
              append(WEARABLE_CONFIGURATION_CATEGORY)
            }
          }
          context.report(
            ISSUE,
            metaData,
            context.getLocation(metaData),
            message
          )
        }
      }
    }
  }

  private fun isWatchFaceService(service: Element): Boolean {
    val intentFilters = service.getElementsByTagName(TAG_INTENT_FILTER)
    for (i in 0 until intentFilters.length) {
      val intentFilter = intentFilters.item(i) as? Element ?: continue
      val actions = intentFilter.getElementsByTagName(TAG_ACTION)
      for (j in 0 until actions.length) {
        val action = actions.item(j) as? Element ?: continue
        if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_SERVICE_ACTION) {
          return true
        }
      }
    }
    return false
  }

  private fun hasConfigurationActivity(application: Element, requiresCategory: Boolean): Boolean {
    val activities = application.getElementsByTagName(TAG_ACTIVITY)
    for (i in 0 until activities.length) {
      val activity = activities.item(i) as? Element ?: continue
      val intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER)
      for (j in 0 until intentFilters.length) {
        val intentFilter = intentFilters.item(j) as? Element ?: continue
        if (intentFilter.containsChildWithName(TAG_ACTION, WATCH_FACE_EDITOR) &&
            (!requiresCategory || intentFilter.containsChildWithName(TAG_CATEGORY, WEARABLE_CONFIGURATION_CATEGORY))) {
          return true
        }
      }
    }
    return false
  }

  private fun Element.containsChildWithName(tag: String, nameValue: String): Boolean {
    val children = getElementsByTagName(tag)
    for (i in 0 until children.length) {
      val child = children.item(i) as? Element ?: continue
      if (child.getAttributeNS(ANDROID_URI, ATTR_NAME) == nameValue) {
        return true
      }
    }
    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "WearableConfigurationAction",
      briefDescription = "Wearable configuration action metadata must match an activity",
      explanation = "When a watch face service declares a `wearableConfigurationAction` metadata " +
        "with the value `WATCH_FACE_EDITOR`, there must be an activity in the same app with an " +
        "intent filter that handles the `WATCH_FACE_EDITOR` action. If `minSdkVersion` is less " +
        "than 30, the intent filter must also include the " +
        "`com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.",
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