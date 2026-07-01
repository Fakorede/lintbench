package com.android.tools.lint.checks

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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

  private val serviceActions = mutableMapOf<String, MutableSet<String>>()
  private val activityCandidates = mutableMapOf<String, MutableList<ActivityInfo>>()

  override fun getApplicableElements(): Collection<String> =
    listOf(TAG_SERVICE, TAG_ACTIVITY)

  override fun beforeCheckProject(context: Context) {
    super.beforeCheckProject(context)
    serviceActions.clear()
    activityCandidates.clear()
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val manifest = context.document.documentElement ?: return
    val packageName = manifest.getAttribute(ATTR_PACKAGE)
    if (packageName.isEmpty()) return

    when (element.tagName) {
      TAG_SERVICE -> collectServiceActions(element, packageName)
      TAG_ACTIVITY -> collectActivity(context, element, packageName)
    }
  }

  private fun collectServiceActions(element: org.w3c.dom.Element, packageName: String) {
    val metadataElements = element.getElementsByTagName(TAG_METADATA)
    for (i in 0 until metadataElements.length) {
      val metadata = metadataElements.item(i) as? org.w3c.dom.Element ?: continue
      val name = metadata.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (name != WEARABLE_CONFIGURATION_ACTION) continue
      val value = metadata.getAttributeNS(ANDROID_URI, ATTR_VALUE)
      if (value == WATCH_FACE_EDITOR) {
        serviceActions.getOrPut(packageName) { mutableSetOf() }.add(value)
      }
    }
  }

  private fun collectActivity(
    context: XmlContext,
    element: org.w3c.dom.Element,
    packageName: String
  ) {
    val location = context.getLocation(element)
    val filters = mutableListOf<IntentFilterInfo>()
    val intentFilterElements = element.getElementsByTagName(TAG_INTENT_FILTER)
    for (i in 0 until intentFilterElements.length) {
      val filter = intentFilterElements.item(i) as? org.w3c.dom.Element ?: continue
      val actions = mutableSetOf<String>()
      val categories = mutableSetOf<String>()

      val actionElements = filter.getElementsByTagName(TAG_ACTION)
      for (j in 0 until actionElements.length) {
        val action = actionElements.item(j) as? org.w3c.dom.Element ?: continue
        val actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (actionName.isNotBlank()) actions.add(actionName)
      }

      val categoryElements = filter.getElementsByTagName(TAG_CATEGORY)
      for (j in 0 until categoryElements.length) {
        val category = categoryElements.item(j) as? org.w3c.dom.Element ?: continue
        val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (categoryName.isNotBlank()) categories.add(categoryName)
      }

      filters.add(IntentFilterInfo(actions, categories))
    }
    activityCandidates.getOrPut(packageName) { mutableListOf() }
      .add(ActivityInfo(filters, location))
  }

  override fun checkMergedProject(context: Context) {
    val requireCategory = context.project.minSdk < 30
    for ((packageName, actions) in serviceActions) {
      val activities = activityCandidates[packageName] ?: continue
      for (action in actions) {
        val matches = activities.filter { it.matches(action, requireCategory) }
        if (matches.size > 1) {
          for (activity in matches) {
            context.report(
              ISSUE,
              activity.location,
              "Duplicate watch face configuration activities found for action \"$action\""
            )
          }
        }
      }
    }
    serviceActions.clear()
    activityCandidates.clear()
  }

  private data class IntentFilterInfo(
    val actions: Set<String>,
    val categories: Set<String>
  )

  private data class ActivityInfo(
    val filters: List<IntentFilterInfo>,
    val location: Location
  ) {
    fun matches(action: String, requireCategory: Boolean): Boolean =
      filters.any {
        action in it.actions &&
          (!requireCategory || WEARABLE_CONFIGURATION_CATEGORY in it.categories)
      }
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val ATTR_NAME = "name"
    private const val ATTR_VALUE = "value"
    private const val ATTR_PACKAGE = "package"
    private const val TAG_SERVICE = "service"
    private const val TAG_ACTIVITY = "activity"
    private const val TAG_METADATA = "meta-data"
    private const val TAG_INTENT_FILTER = "intent-filter"
    private const val TAG_ACTION = "action"
    private const val TAG_CATEGORY = "category"
    private const val WATCH_FACE_EDITOR = "WATCH_FACE_EDITOR"
    private const val WEARABLE_CONFIGURATION_ACTION =
      "com.google.android.wearable.watchface.wearableConfigurationAction"
    private const val WEARABLE_CONFIGURATION_CATEGORY =
      "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

    @JvmField
    val ISSUE = Issue.create(
      id = "WearableActionDuplicate",
      briefDescription = "Duplicate watch face configuration activities found",
      explanation = """
        If a watch face service declares a `wearableConfigurationAction` metadata value of
        `WATCH_FACE_EDITOR`, there should be exactly one activity in the same package with an
        intent filter for that action. On devices running Wear OS earlier than API 30, that
        intent filter must also include the
        `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` category.
        Having more than one matching activity is an error.
      """.trimIndent(),
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