package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: Context) {
    val mergedManifest = context.mainProject.mergedManifest ?: return
    val root = mergedManifest.documentElement ?: return

    val services = root.getElementsByTagName(com.android.SdkConstants.TAG_SERVICE)
    val servicesWithConfig = mutableListOf<org.w3c.dom.Element>()
    for (i in 0 until services.length) {
      val service = services.item(i) as org.w3c.dom.Element
      var child = com.android.utils.XmlUtils.getFirstSubTagByName(service, com.android.SdkConstants.TAG_META_DATA)
      while (child != null) {
        val name = child.getAttributeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_NAME)
        val value = child.getAttributeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_VALUE)
        if (name == "com.google.android.wearable.watchface.wearableConfigurationAction" &&
            value == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
          servicesWithConfig.add(service)
        }
        child = com.android.utils.XmlUtils.getNextTagByName(child, com.android.SdkConstants.TAG_META_DATA)
      }
    }

    val activities = root.getElementsByTagName(com.android.SdkConstants.TAG_ACTIVITY)
    val configActivities = mutableListOf<org.w3c.dom.Element>()
    for (i in 0 until activities.length) {
      val activity = activities.item(i) as org.w3c.dom.Element
      var intentFilter = com.android.utils.XmlUtils.getFirstSubTagByName(activity, com.android.SdkConstants.TAG_INTENT_FILTER)
      var hasAction = false
      var hasCategory = false
      while (intentFilter != null) {
        var action = com.android.utils.XmlUtils.getFirstSubTagByName(intentFilter, com.android.SdkConstants.TAG_ACTION)
        while (action != null) {
          if (action.getAttributeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_NAME) == "com.google.android.wearable.watchface.configuration.WATCH_FACE_EDITOR") {
            hasAction = true
          }
          action = com.android.utils.XmlUtils.getNextTagByName(action, com.android.SdkConstants.TAG_ACTION)
        }
        var category = com.android.utils.XmlUtils.getFirstSubTagByName(intentFilter, com.android.SdkConstants.TAG_CATEGORY)
        while (category != null) {
          if (category.getAttributeNS(com.android.SdkConstants.ANDROID_URI, com.android.SdkConstants.ATTR_NAME) == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
            hasCategory = true
          }
          category = com.android.utils.XmlUtils.getNextTagByName(category, com.android.SdkConstants.TAG_CATEGORY)
        }
        intentFilter = com.android.utils.XmlUtils.getNextTagByName(intentFilter, com.android.SdkConstants.TAG_INTENT_FILTER)
      }

      if (hasAction) {
        val minSdkVersion = context.mainProject.minSdkVersion.apiLevel
        if (minSdkVersion < 30) {
          if (hasCategory) {
            configActivities.add(activity)
          }
        } else {
          configActivities.add(activity)
        }
      }
    }

    if (servicesWithConfig.isNotEmpty() && configActivities.isEmpty()) {
      for (service in servicesWithConfig) {
        context.report(
          ISSUE,
          service,
          context.getLocation(service),
          "A watch face service defines the `wearableConfigurationAction` metadata, but no matching activity with action `WATCH_FACE_EDITOR` was found"
        )
      }
    } else if (servicesWithConfig.isEmpty() && configActivities.isNotEmpty()) {
      for (activity in configActivities) {
        context.report(
          ISSUE,
          activity,
          context.getLocation(activity),
          "An activity with action `WATCH_FACE_EDITOR` is defined, but no watch face service defines the `wearableConfigurationAction` metadata"
        )
      }
    } else if (configActivities.size > 1) {
      for (i in 1 until configActivities.size) {
        val activity = configActivities[i]
        context.report(
          ISSUE,
          activity,
          context.getLocation(activity),
          "Duplicate watch face configuration activities found"
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "WearableActionDuplicate",
      briefDescription = "Duplicate watch face configuration activities found",
      explanation = "If a watch face service defines `wearableConfigurationAction` metadata, " +
        "with the value `WATCH_FACE_EDITOR`, there should be exactly one activity in the same " +
        "package, which has an intent filter for `WATCH_FACE_EDITOR` (with " +
        "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).",
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