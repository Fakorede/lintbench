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
    val manifest = context.mainProject.mergedManifest ?: return
    val root = manifest.documentElement ?: return

    val action = "WATCH_FACE_EDITOR"
    val metaDataName = "wearableConfigurationAction"
    val wearableCategory = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
    val requireCategory = context.mainProject.minSdk < 30

    val services = root.getElementsByTagName(com.android.SdkConstants.TAG_SERVICE)
    val watchFaceServices = mutableListOf<org.w3c.dom.Element>()
    for (i in 0 until services.length) {
      val service = services.item(i) as? org.w3c.dom.Element ?: continue
      val metaData = findWearableConfigurationMetaData(service, metaDataName, action) ?: continue
      watchFaceServices.add(metaData)
    }

    if (watchFaceServices.isEmpty()) return

    val activities = root.getElementsByTagName(com.android.SdkConstants.TAG_ACTIVITY)
    val matchingActivities = mutableListOf<org.w3c.dom.Element>()
    for (i in 0 until activities.length) {
      val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
      if (activityHandlesAction(activity, action, requireCategory, wearableCategory)) {
        matchingActivities.add(activity)
      }
    }

    if (matchingActivities.size > 1) {
      for (activity in matchingActivities) {
        context.report(
          ISSUE,
          activity,
          context.getLocation(activity),
          "Duplicate watch face configuration activities found"
        )
      }
    } else if (matchingActivities.isEmpty()) {
      for (metaData in watchFaceServices) {
        context.report(
          ISSUE,
          metaData,
          context.getLocation(metaData),
          "No watch face configuration activity found for WATCH_FACE_EDITOR"
        )
      }
    }
  }

  private fun findWearableConfigurationMetaData(
    service: org.w3c.dom.Element,
    metaDataName: String,
    action: String
  ): org.w3c.dom.Element? {
    val metaDataNodes = service.getElementsByTagName(com.android.SdkConstants.TAG_META_DATA)
    for (i in 0 until metaDataNodes.length) {
      val metaData = metaDataNodes.item(i) as? org.w3c.dom.Element ?: continue
      val name = metaData.getAttributeNS(
        com.android.SdkConstants.ANDROID_URI,
        com.android.SdkConstants.ATTR_NAME
      )
      if (name != metaDataName) continue
      val value = metaData.getAttributeNS(
        com.android.SdkConstants.ANDROID_URI,
        com.android.SdkConstants.ATTR_VALUE
      )
      if (value == action) return metaData
    }
    return null
  }

  private fun activityHandlesAction(
    activity: org.w3c.dom.Element,
    action: String,
    requireCategory: Boolean,
    categoryName: String
  ): Boolean {
    val intentFilters = activity.getElementsByTagName(com.android.SdkConstants.TAG_INTENT_FILTER)
    for (i in 0 until intentFilters.length) {
      val intentFilter = intentFilters.item(i) as? org.w3c.dom.Element ?: continue
      if (intentFilterMatches(intentFilter, action, requireCategory, categoryName)) {
        return true
      }
    }
    return false
  }

  private fun intentFilterMatches(
    intentFilter: org.w3c.dom.Element,
    action: String,
    requireCategory: Boolean,
    categoryName: String
  ): Boolean {
    val actions = intentFilter.getElementsByTagName(com.android.SdkConstants.TAG_ACTION)
    var hasAction = false
    for (i in 0 until actions.length) {
      val node = actions.item(i) as? org.w3c.dom.Element ?: continue
      val name = node.getAttributeNS(
        com.android.SdkConstants.ANDROID_URI,
        com.android.SdkConstants.ATTR_NAME
      )
      if (name == action) {
        hasAction = true
        break
      }
    }
    if (!hasAction) return false
    if (!requireCategory) return true

    val categories = intentFilter.getElementsByTagName(com.android.SdkConstants.TAG_CATEGORY)
    for (i in 0 until categories.length) {
      val node = categories.item(i) as? org.w3c.dom.Element ?: continue
      val name = node.getAttributeNS(
        com.android.SdkConstants.ANDROID_URI,
        com.android.SdkConstants.ATTR_NAME
      )
      if (name == categoryName) return true
    }
    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "WearableActionDuplicate",
      briefDescription = "Duplicate watch face configuration activities found",
      explanation = "If and only if a watch face service defines `wearableConfigurationAction` metadata, " +
        "with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, which " +
        "has an intent filter for `WATCH_FACE_EDITOR` (with " +
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