package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_METADATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import com.android.utils.XmlUtils.parseUtfXmlFile
import org.w3c.dom.Document
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> = emptyList()

  override fun checkMergedProject(context: Context) {
    val mergedManifest = context.mainProject.mergedManifest ?: return
    val document: Document = parseUtfXmlFile(mergedManifest, true)
    val application = getFirstSubTagByName(document.documentElement, TAG_APPLICATION) ?: return
    val minSdk = context.mainProject.minSdk

    val configurationActivityCount = countConfigurationActivities(application, minSdk)

    var service = getFirstSubTagByName(application, TAG_SERVICE)
    while (service != null) {
      if (service.isWatchFaceService()) {
        val metadata = service.findWearableConfigurationAction()
        if (metadata != null && configurationActivityCount > 1) {
          context.report(
            ISSUE,
            metadata,
            context.getLocation(metadata),
            "Duplicate watch face configuration activities found"
          )
        }
      }
      service = getNextTagByName(service, TAG_SERVICE)
    }
  }

  private fun Element.isWatchFaceService(): Boolean {
    var filter = getFirstSubTagByName(this, TAG_INTENT_FILTER)
    while (filter != null) {
      var action = getFirstSubTagByName(filter, TAG_ACTION)
      while (action != null) {
        if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_UPDATE) {
          return true
        }
        action = getNextTagByName(action, TAG_ACTION)
      }
      filter = getNextTagByName(filter, TAG_INTENT_FILTER)
    }
    return false
  }

  private fun Element.findWearableConfigurationAction(): Element? {
    var metadata = getFirstSubTagByName(this, TAG_METADATA)
    while (metadata != null) {
      val name = metadata.getAttributeNS(ANDROID_URI, ATTR_NAME)
      val value = metadata.getAttributeNS(ANDROID_URI, ATTR_VALUE)
      if (name == WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
        return metadata
      }
      metadata = getNextTagByName(metadata, TAG_METADATA)
    }
    return null
  }

  private fun countConfigurationActivities(application: Element, minSdk: Int): Int {
    var count = 0
    var activity = getFirstSubTagByName(application, TAG_ACTIVITY)
    while (activity != null) {
      if (activity.hasConfigurationAction(minSdk)) {
        count++
      }
      activity = getNextTagByName(activity, TAG_ACTIVITY)
    }
    return count
  }

  private fun Element.hasConfigurationAction(minSdk: Int): Boolean {
    var filter = getFirstSubTagByName(this, TAG_INTENT_FILTER)
    while (filter != null) {
      var hasAction = false
      var hasCategory = false

      var action = getFirstSubTagByName(filter, TAG_ACTION)
      while (action != null) {
        if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR) {
          hasAction = true
        }
        action = getNextTagByName(action, TAG_ACTION)
      }

      if (minSdk < 30) {
        var category = getFirstSubTagByName(filter, TAG_CATEGORY)
        while (category != null) {
          if (category.getAttributeNS(ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_CATEGORY) {
            hasCategory = true
          }
          category = getNextTagByName(category, TAG_CATEGORY)
        }
      }

      if (hasAction && (minSdk >= 30 || hasCategory)) {
        return true
      }
      filter = getNextTagByName(filter, TAG_INTENT_FILTER)
    }
    return false
  }

  companion object {
    private const val WATCH_FACE_UPDATE = "com.google.android.wearable.watchface.WATCH_FACE_UPDATE"
    private const val WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
    private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
    private const val WEARABLE_CONFIGURATION_CATEGORY = "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

    @JvmField
    val ISSUE = Issue.create(
      id = "WearableActionDuplicate",
      briefDescription = "Duplicate watch face configuration activities found",
      explanation = "If and only if a watch face service defines a `wearableConfigurationAction` " +
        "metadata element with the value `WATCH_FACE_EDITOR`, there should be exactly one " +
        "activity in the same package with an intent filter for `WATCH_FACE_EDITOR`. " +
        "When `minSdkVersion` is less than 30, that intent filter must also include the " +
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