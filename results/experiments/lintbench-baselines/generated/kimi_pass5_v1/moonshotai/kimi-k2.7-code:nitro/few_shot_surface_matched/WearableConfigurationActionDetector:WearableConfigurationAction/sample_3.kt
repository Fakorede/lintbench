package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: Context) {
    val xmlContext = context as? XmlContext ?: return
    val document = xmlContext.document ?: return
    val manifest = document.documentElement ?: return
    val application = manifest.getElementsByTagName(TAG_APPLICATION).item(0) as? org.w3c.dom.Element ?: return

    val minSdk = xmlContext.mainProject.minSdkVersion

    val activitiesWithAction = mutableSetOf<String>()
    val activitiesWithActionAndCategory = mutableSetOf<String>()

    val activities = application.getElementsByTagName(TAG_ACTIVITY)
    for (i in 0 until activities.length) {
      val activity = activities.item(i) as? org.w3c.dom.Element ?: continue
      val activityName = activity.getAttributeNS(ANDROID_URI, ATTR_NAME)
      val intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER)
      for (j in 0 until intentFilters.length) {
        val intentFilter = intentFilters.item(j) as? org.w3c.dom.Element ?: continue
        if (intentFilterHasAction(intentFilter, ACTION_WATCH_FACE_EDITOR)) {
          activitiesWithAction.add(activityName)
          if (minSdk >= 30 || intentFilterHasCategory(intentFilter, CATEGORY_WEARABLE_CONFIGURATION)) {
            activitiesWithActionAndCategory.add(activityName)
          }
        }
      }
    }

    val services = application.getElementsByTagName(TAG_SERVICE)
    for (i in 0 until services.length) {
      val service = services.item(i) as? org.w3c.dom.Element ?: continue
      val serviceName = service.getAttributeNS(ANDROID_URI, ATTR_NAME)
      val metadataElements = service.getElementsByTagName(TAG_METADATA)
      for (j in 0 until metadataElements.length) {
        val metadata = metadataElements.item(j) as? org.w3c.dom.Element ?: continue
        if (metadata.getAttributeNS(ANDROID_URI, ATTR_NAME) != META_DATA_NAME) continue
        if (metadata.getAttributeNS(ANDROID_URI, ATTR_VALUE) != ACTION_WATCH_FACE_EDITOR) continue

        val hasMatchingActivity = if (minSdk < 30) {
          activitiesWithActionAndCategory.isNotEmpty()
        } else {
          activitiesWithAction.isNotEmpty()
        }

        if (!hasMatchingActivity) {
          val message = buildString {
            append("The watch face service `$serviceName` declares a wearableConfigurationAction of ")
            append(ACTION_WATCH_FACE_EDITOR)
            append(", but no activity in the same package has an intent filter with action ")
            append(ACTION_WATCH_FACE_EDITOR)
            if (minSdk < 30) {
              append(" and category ")
              append(CATEGORY_WEARABLE_CONFIGURATION)
            }
            append(".")
          }
          xmlContext.report(ISSUE, metadata, xmlContext.getLocation(metadata), message)
        }
      }
    }
  }

  private fun intentFilterHasAction(intentFilter: org.w3c.dom.Element, actionName: String): Boolean {
    val actions = intentFilter.getElementsByTagName(TAG_ACTION)
    for (i in 0 until actions.length) {
      val action = actions.item(i) as? org.w3c.dom.Element ?: continue
      if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == actionName) {
        return true
      }
    }
    return false
  }

  private fun intentFilterHasCategory(intentFilter: org.w3c.dom.Element, categoryName: String): Boolean {
    val categories = intentFilter.getElementsByTagName(TAG_CATEGORY)
    for (i in 0 until categories.length) {
      val category = categories.item(i) as? org.w3c.dom.Element ?: continue
      if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == categoryName) {
        return true
      }
    }
    return false
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val TAG_APPLICATION = "application"
    private const val TAG_ACTIVITY = "activity"
    private const val TAG_SERVICE = "service"
    private const val TAG_METADATA = "meta-data"
    private const val TAG_INTENT_FILTER = "intent-filter"
    private const val TAG_ACTION = "action"
    private const val TAG_CATEGORY = "category"
    private const val ATTR_NAME = "name"
    private const val ATTR_VALUE = "value"
    private const val META_DATA_NAME = "wearableConfigurationAction"
    private const val ACTION_WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
    private const val CATEGORY_WEARABLE_CONFIGURATION = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

    @JvmField
    val ISSUE = Issue.create(
      id = "WearableConfigurationAction",
      briefDescription = "Wearable configuration action metadata must match an activity",
      explanation = "When a watch face service declares a meta-data element with " +
        "android:name=\"wearableConfigurationAction\" and android:value=\"WATCH_FACE_EDITOR\", " +
        "there must be a configuration activity in the same package that has an intent filter " +
        "with action WATCH_FACE_EDITOR. If minSdkVersion is less than 30, the intent filter must " +
        "also include the category " +
        "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE),
    )
  }
}