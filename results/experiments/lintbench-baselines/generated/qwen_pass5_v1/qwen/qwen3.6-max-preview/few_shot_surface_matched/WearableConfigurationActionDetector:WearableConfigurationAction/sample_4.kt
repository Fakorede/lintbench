package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: Context) {
    val manifestFile = context.mainProject.mergedManifest ?: return
    val document = try {
      XmlUtils.parseDocument(manifestFile, true)
    } catch (e: Exception) {
      return
    } ?: return
    val root = document.documentElement ?: return
    val minSdk = context.mainProject.minSdkVersion

    val services = root.getElementsByTagName(TAG_SERVICE)
    for (i in 0 until services.length) {
      val service = services.item(i) as? Element ?: continue
      var metaData = XmlUtils.getFirstSubTagByName(service, TAG_META_DATA)
      while (metaData != null) {
        val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
        if (name.contains("wearableConfigurationAction", ignoreCase = true) && value == "WATCH_FACE_EDITOR") {
          if (!hasMatchingActivity(root, minSdk)) {
            val message = if (minSdk < 30) {
              "Wearable configuration action metadata requires an activity with an intent filter for WATCH_FACE_EDITOR and the WEARABLE_CONFIGURATION category."
            } else {
              "Wearable configuration action metadata requires an activity with an intent filter for WATCH_FACE_EDITOR."
            }
            context.report(ISSUE, context.getLocation(metaData), message)
          }
        }
        metaData = XmlUtils.getNextTagByName(metaData, TAG_META_DATA)
      }
    }
  }

  private fun hasMatchingActivity(root: Element, minSdk: Int): Boolean {
    val activities = root.getElementsByTagName(TAG_ACTIVITY)
    for (i in 0 until activities.length) {
      val activity = activities.item(i) as? Element ?: continue
      var intentFilter = XmlUtils.getFirstSubTagByName(activity, TAG_INTENT_FILTER)
      while (intentFilter != null) {
        var hasAction = false
        var action = XmlUtils.getFirstSubTagByName(intentFilter, TAG_ACTION)
        while (action != null) {
          if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == "WATCH_FACE_EDITOR") {
            hasAction = true
          }
          action = XmlUtils.getNextTagByName(action, TAG_ACTION)
        }

        if (hasAction) {
          if (minSdk < 30) {
            var hasCategory = false
            var category = XmlUtils.getFirstSubTagByName(intentFilter, TAG_CATEGORY)
            while (category != null) {
              if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                hasCategory = true
              }
              category = XmlUtils.getNextTagByName(category, TAG_CATEGORY)
            }
            if (hasCategory) return true
          } else {
            return true
          }
        }
        intentFilter = XmlUtils.getNextTagByName(intentFilter, TAG_INTENT_FILTER)
      }
    }
    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "WearableConfigurationAction",
      briefDescription = "Wearable configuration action metadata must match an activity",
      explanation = "When a watch face service defines wearableConfigurationAction metadata with the value WATCH_FACE_EDITOR, there should be an activity in the same package with an intent filter for WATCH_FACE_EDITOR. If minSdkVersion is less than 30, the intent filter must also include the WEARABLE_CONFIGURATION category.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
    )
  }
}