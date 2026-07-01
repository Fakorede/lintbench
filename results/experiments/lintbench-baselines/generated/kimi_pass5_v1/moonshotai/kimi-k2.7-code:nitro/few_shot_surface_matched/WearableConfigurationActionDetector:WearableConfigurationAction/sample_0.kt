package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.MergedManifestMergeContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: MergedManifestMergeContext) {
    val manifest = context.document.documentElement ?: return
    if (manifest.tagName != "manifest") return

    val packageName = manifest.getAttribute("package") ?: ""
    val minSdk = context.project.minSdk

    val editorMetadata = mutableListOf<org.w3c.dom.Element>()
    for (service in manifest.children("service")) {
      for (metadata in service.children("meta-data")) {
        val name = metadata.attr(ANDROID_URI, "name")
        val value = metadata.attr(ANDROID_URI, "value")
        if (name == WEARABLE_CONFIGURATION_ACTION && value == WATCH_FACE_EDITOR) {
          editorMetadata.add(metadata)
        }
      }
    }

    if (editorMetadata.isEmpty()) return

    val matchingActivities = mutableListOf<ActivityMatch>()
    for (activity in manifest.children("activity")) {
      val activityName = activity.attr(ANDROID_URI, "name") ?: continue
      if (!isInSamePackage(packageName, activityName)) continue

      var hasAction = false
      var hasCategory = false
      for (filter in activity.children("intent-filter")) {
        val actions = filter.children("action").mapNotNull { it.attr(ANDROID_URI, "name") }
        if (WATCH_FACE_EDITOR in actions) {
          hasAction = true
          val categories = filter.children("category").mapNotNull { it.attr(ANDROID_URI, "name") }
          if (WEARABLE_CONFIGURATION_CATEGORY in categories) {
            hasCategory = true
          }
        }
      }

      if (hasAction) {
        matchingActivities.add(ActivityMatch(activity, hasCategory))
      }
    }

    for (metadata in editorMetadata) {
      val valid = matchingActivities.any { it.hasConfigurationCategory || minSdk >= 30 }
      if (!valid) {
        val message = buildString {
          append("The wearableConfigurationAction metadata with value \"")
          append(WATCH_FACE_EDITOR)
          append("\" must have a matching activity in the same package with an intent filter for action \"")
          append(WATCH_FACE_EDITOR)
          append("\"")
          if (minSdk < 30) {
            append(" and category \"")
            append(WEARABLE_CONFIGURATION_CATEGORY)
            append("\"")
          }
          append(".")
        }
        context.report(
          ISSUE,
          metadata,
          context.getElementLocation(metadata),
          message
        )
      }
    }
  }

  private fun org.w3c.dom.Element.children(tagName: String): List<org.w3c.dom.Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? org.w3c.dom.Element }
      .filter { it.tagName == tagName }

  private fun org.w3c.dom.Element.attr(namespace: String, name: String): String? =
    getAttributeNodeNS(namespace, name)?.value

  private fun isInSamePackage(packageName: String, className: String): Boolean {
    if (packageName.isEmpty()) return true
    val fullName = when {
      className.startsWith(".") -> packageName + className
      '.' in className -> className
      else -> "$packageName.$className"
    }
    return fullName == packageName || fullName.startsWith("$packageName.")
  }

  private data class ActivityMatch(
    val element: org.w3c.dom.Element,
    val hasConfigurationCategory: Boolean
  )

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val WEARABLE_CONFIGURATION_ACTION = "wearableConfigurationAction"
    private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
    private const val WEARABLE_CONFIGURATION_CATEGORY =
      "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

    @JvmField
    val ISSUE = Issue.create(
      id = "WearableConfigurationAction",
      briefDescription = "Wearable configuration action does not match an activity",
      explanation = "When a watch face service declares a `wearableConfigurationAction` metadata " +
        "value of `WATCH_FACE_EDITOR`, there must be an activity in the same package with an " +
        "intent filter for the `WATCH_FACE_EDITOR` action. If `minSdkVersion` is less than 30, " +
        "the intent filter must also include the " +
        "`com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        WearableConfigurationActionDetector::class.java,
        Scope.MERGED_MANIFEST_SCOPE
      )
    )
  }
}