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
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: Context) {
    if (context !is XmlContext) return
    val manifest = context.document.documentElement ?: return
    val application = getFirstSubTagByName(manifest, TAG_APPLICATION) ?: return
    val packageName = manifest.getAttribute("package")

    val activityActions = mutableSetOf<String>()
    val activityCategories = mutableSetOf<String>()

    var activity: Element? = getFirstSubTagByName(application, TAG_ACTIVITY)
    while (activity != null) {
      if (isInPackage(activity, packageName)) {
        var filter: Element? = getFirstSubTagByName(activity, TAG_INTENT_FILTER)
        while (filter != null) {
          collectActions(filter, activityActions)
          collectCategories(filter, activityCategories)
          filter = getNextTagByName(filter, TAG_INTENT_FILTER)
        }
      }
      activity = getNextTagByName(activity, TAG_ACTIVITY)
    }

    val needsCategory = context.mainProject.minSdk < 30

    var service: Element? = getFirstSubTagByName(application, TAG_SERVICE)
    while (service != null) {
      val action = getWearableConfigurationAction(service)
      if (action != null) {
        val location = context.getLocation(service)
        if (action !in activityActions) {
          context.report(
            ISSUE,
            service,
            location,
            "Watch face service declares wearable configuration action '$action', but no " +
              "activity in the same package has a matching intent filter."
          )
        } else if (needsCategory && WEARABLE_CONFIGURATION_CATEGORY !in activityCategories) {
          context.report(
            ISSUE,
            service,
            location,
            "Watch face service declares wearable configuration action '$action'. For " +
              "minSdkVersion < 30, the configuration activity must include the category " +
              "'$WEARABLE_CONFIGURATION_CATEGORY' in its intent filter."
          )
        }
      }
      service = getNextTagByName(service, TAG_SERVICE)
    }
  }

  private fun getWearableConfigurationAction(service: Element): String? {
    var metaData: Element? = getFirstSubTagByName(service, TAG_META_DATA)
    while (metaData != null) {
      val name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (name == WEARABLE_CONFIGURATION_ACTION_NAME) {
        val value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE)
        if (value == WATCH_FACE_EDITOR_VALUE) {
          return value
        }
      }
      metaData = getNextTagByName(metaData, TAG_META_DATA)
    }
    return null
  }

  private fun collectActions(filter: Element, actions: MutableSet<String>) {
    var action: Element? = getFirstSubTagByName(filter, TAG_ACTION)
    while (action != null) {
      val name = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (name.isNotEmpty()) {
        actions.add(name)
      }
      action = getNextTagByName(action, TAG_ACTION)
    }
  }

  private fun collectCategories(filter: Element, categories: MutableSet<String>) {
    var category: Element? = getFirstSubTagByName(filter, TAG_CATEGORY)
    while (category != null) {
      val name = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (name.isNotEmpty()) {
        categories.add(name)
      }
      category = getNextTagByName(category, TAG_CATEGORY)
    }
  }

  private fun isInPackage(element: Element, packageName: String): Boolean {
    val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
    if (name.isEmpty()) return false
    if (name.startsWith(".") || name.indexOf('.') == -1) return true
    return name == packageName || name.startsWith("$packageName.")
  }

  companion object {
    const val WEARABLE_CONFIGURATION_ACTION_NAME = "wearableConfigurationAction"
    const val WATCH_FACE_EDITOR_VALUE = "WATCH_FACE_EDITOR"
    const val WEARABLE_CONFIGURATION_CATEGORY =
      "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

    @JvmField
    val ISSUE = Issue.create(
      id = "WearableConfigurationAction",
      briefDescription = "Wearable configuration action must match an activity",
      explanation = "When a watch face service declares a wearableConfigurationAction " +
        "metadata value of WATCH_FACE_EDITOR, there must be an activity in the same package " +
        "with an intent filter for that action. On devices with minSdkVersion < 30, the " +
        "activity's intent filter must also include the category " +
        "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        WearableConfigurationActionDetector::class.java,
        Scope.MANIFEST_SCOPE
      ),
    )
  }
}