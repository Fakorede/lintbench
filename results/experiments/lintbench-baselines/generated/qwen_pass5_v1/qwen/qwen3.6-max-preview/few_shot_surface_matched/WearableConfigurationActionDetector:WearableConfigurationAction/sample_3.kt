package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
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
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: Context, project: Project, mergedManifest: Element) {
    val xmlContext = context as? XmlContext ?: return
    val minSdk = xmlContext.mainProject.minSdkVersion ?: 1
    val requiresCategory = minSdk < 30

    val application = getFirstSubTagByName(mergedManifest, TAG_APPLICATION) ?: return

    var hasMatchingActivity = false
    var activity = getFirstSubTagByName(application, TAG_ACTIVITY)
    while (activity != null) {
      if (activityMatches(activity, requiresCategory)) {
        hasMatchingActivity = true
        break
      }
      activity = getNextTagByName(activity, TAG_ACTIVITY)
    }

    if (hasMatchingActivity) return

    var service = getFirstSubTagByName(application, TAG_SERVICE)
    while (service != null) {
      val metadata = findMatchingMetadata(service)
      if (metadata != null) {
        xmlContext.report(
          ISSUE,
          metadata,
          xmlContext.getLocation(metadata),
          "Watch face service defines wearableConfigurationAction metadata but no matching activity with WATCH_FACE_EDITOR intent filter exists in the same package."
        )
      }
      service = getNextTagByName(service, TAG_SERVICE)
    }
  }

  private fun activityMatches(activity: Element, requiresCategory: Boolean): Boolean {
    var intentFilter = getFirstSubTagByName(activity, TAG_INTENT_FILTER)
    while (intentFilter != null) {
      var hasAction = false
      var hasCategory = !requiresCategory

      var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
      while (action != null) {
        if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == "WATCH_FACE_EDITOR") {
          hasAction = true
        }
        action = getNextTagByName(action, TAG_ACTION)
      }

      if (hasAction && requiresCategory) {
        var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
        while (category != null) {
          if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
            hasCategory = true
            break
          }
          category = getNextTagByName(category, TAG_CATEGORY)
        }
      }

      if (hasAction && hasCategory) return true
      intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
    }
    return false
  }

  private fun findMatchingMetadata(service: Element): Element? {
    var metadata = getFirstSubTagByName(service, TAG_META_DATA)
    while (metadata != null) {
      val name = metadata.getAttributeNS(ANDROID_URI, ATTR_NAME)
      val value = metadata.getAttributeNS(ANDROID_URI, ATTR_VALUE)
      if (name == "wearableConfigurationAction" && value == "WATCH_FACE_EDITOR") {
        return metadata
      }
      metadata = getNextTagByName(metadata, TAG_META_DATA)
    }
    return null
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "WearableConfigurationAction",
      briefDescription = "Wearable configuration action metadata must match an activity",
      explanation = "When a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there must be an activity in the same package with an intent filter for WATCH_FACE_EDITOR. If minSdkVersion is less than 30, the intent filter must also include the com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION category.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
    )
  }
}