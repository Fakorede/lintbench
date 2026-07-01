package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_MANIFEST)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val minSdk = context.mainProject.minSdkVersion

    var hasConfigAction = false
    var serviceElement: Element? = null
    var service = getFirstSubTagByName(element, TAG_SERVICE)
    while (service != null) {
      var metaData = getFirstSubTagByName(service, TAG_META_DATA)
      while (metaData != null) {
        val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
        if ((name == "wearableConfigurationAction" ||
            name == "com.google.android.wearable.watchface.wearableConfigurationAction") &&
          value == "WATCH_FACE_EDITOR") {
          hasConfigAction = true
          serviceElement = service
          break
        }
        metaData = getNextTagByName(metaData, TAG_META_DATA)
      }
      if (hasConfigAction) break
      service = getNextTagByName(service, TAG_SERVICE)
    }

    if (!hasConfigAction || serviceElement == null) return

    val matchingActivities = mutableListOf<Element>()
    var activity = getFirstSubTagByName(element, TAG_ACTIVITY)
    while (activity != null) {
      var intentFilter = getFirstSubTagByName(activity, TAG_INTENT_FILTER)
      while (intentFilter != null) {
        var hasAction = false
        var hasCategory = false

        var action = getFirstSubTagByName(intentFilter, TAG_ACTION)
        while (action != null) {
          if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == "WATCH_FACE_EDITOR") {
            hasAction = true
          }
          action = getNextTagByName(action, TAG_ACTION)
        }

        if (minSdk < 30) {
          var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
          while (category != null) {
            if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) ==
              "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
              hasCategory = true
            }
            category = getNextTagByName(category, TAG_CATEGORY)
          }
        }

        val matches = if (minSdk < 30) hasAction && hasCategory else hasAction
        if (matches) {
          matchingActivities.add(activity)
          break
        }
        intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
      }
      activity = getNextTagByName(activity, TAG_ACTIVITY)
    }

    if (matchingActivities.size > 1) {
      context.report(
        ISSUE,
        serviceElement,
        context.getLocation(serviceElement),
        "Duplicate watch face configuration activities found"
      )
    } else if (matchingActivities.isEmpty()) {
      context.report(
        ISSUE,
        serviceElement,
        context.getLocation(serviceElement),
        "Missing watch face configuration activity for WATCH_FACE_EDITOR"
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "WearableActionDuplicate",
      briefDescription = "Duplicate watch face configuration activities found",
      explanation = "If a watch face service defines wearableConfigurationAction metadata with the value " +
        "WATCH_FACE_EDITOR, there should be exactly one activity in the same package with an intent filter " +
        "for WATCH_FACE_EDITOR (and com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION " +
        "if minSdkVersion < 30). Duplicate or missing configuration activities can cause unexpected behavior.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE)
    )
  }
}