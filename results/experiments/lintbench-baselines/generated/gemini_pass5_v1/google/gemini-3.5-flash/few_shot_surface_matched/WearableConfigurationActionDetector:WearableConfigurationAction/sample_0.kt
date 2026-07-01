package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_INTENT_FILTER
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
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  private val metadataList = mutableListOf<MetadataInfo>()
  private val activityList = mutableListOf<ActivityInfo>()

  private class MetadataInfo(
    val value: String,
    val location: Location
  )

  private class ActivityInfo(
    val actions: List<String>,
    val categories: List<String>
  )

  override fun getApplicableElements(): Collection<String> {
    return listOf("meta-data", "activity")
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val tagName = element.tagName
    if (tagName == "meta-data") {
      val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (name == "com.google.android.wearable.watchface.wearableConfigurationAction") {
        val value = element.getAttributeNS(ANDROID_URI, "value")
        if (value.isNotEmpty() && (value == "WATCH_FACE_EDITOR" || value.contains("WATCH_FACE_EDITOR"))) {
          metadataList.add(MetadataInfo(value, context.getLocation(element)))
        }
      }
    } else if (tagName == "activity") {
      val actions = mutableListOf<String>()
      val categories = mutableListOf<String>()
      var intentFilter = getFirstSubTagByName(element, TAG_INTENT_FILTER)
      while (intentFilter != null) {
        var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
        while (action != null) {
          val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
          if (actionName.isNotEmpty()) {
            actions.add(actionName)
          }
          action = getNextTagByName(action, TAG_ACTION)
        }
        var category = getFirstSubTagByName(intentFilter, "category")
        while (category != null) {
          val catName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
          if (catName.isNotEmpty()) {
            categories.add(catName)
          }
          category = getNextTagByName(category, "category")
        }
        intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
      }
      activityList.add(ActivityInfo(actions, categories))
    }
  }

  override fun checkMergedProject(context: Context) {
    val minSdk = context.project.minSdkVersion.apiLevel
    for (metadata in metadataList) {
      val expectedAction = metadata.value
      var matched = false
      for (activity in activityList) {
        if (activity.actions.contains(expectedAction)) {
          if (minSdk < 30) {
            if (activity.categories.contains("com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION")) {
              matched = true
              break
            }
          } else {
            matched = true
            break
          }
        }
      }
      if (!matched) {
        val message = if (minSdk < 30) {
          "Wearable configuration action `$expectedAction` requires a matching activity with an intent filter for `$expectedAction` and category `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION`"
        } else {
          "Wearable configuration action `$expectedAction` requires a matching activity with an intent filter for `$expectedAction`"
        }
        context.report(ISSUE, metadata.location, message)
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "WearableConfigurationAction",
      briefDescription = "Wearable configuration action metadata must match an activity",
      explanation = "When a watch face service defines `wearableConfigurationAction` metadata, " +
        "there should be an activity in the same package which has an intent filter for " +
        "that action (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` " +
        "if `minSdkVersion` is less than 30).",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE),
    )
  }
}