package com.android.tools.lint.checks

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
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), Detector.XmlScanner {

    private val servicesWithConfigAction = mutableListOf<Element>()
    private val activitiesWithEditorFilter = mutableListOf<Element>()

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_SERVICE, TAG_ACTIVITY)

    override fun beforeCheckFile(context: Context) {
        servicesWithConfigAction.clear()
        activitiesWithEditorFilter.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_SERVICE -> {
                if (hasWearableConfigurationAction(element)) {
                    servicesWithConfigAction.add(element)
                }
            }
            TAG_ACTIVITY -> {
                if (hasWatchFaceEditorAction(element)) {
                    activitiesWithEditorFilter.add(element)
                }
            }
        }
    }

    private fun hasWearableConfigurationAction(serviceElement: Element): Boolean {
        val metadataList = serviceElement.getElementsByTagName(TAG_META_DATA)
        for (i in 0 until metadataList.length) {
            val metadata = metadataList.item(i) as? Element ?: continue
            if (metadata.getAttribute(ATTR_NAME) == "wearableConfigurationAction" &&
                metadata.getAttribute(ATTR_VALUE) == "WATCH_FACE_EDITOR") {
                return true
            }
        }
        return false
    }

    private fun hasWatchFaceEditorAction(activityElement: Element): Boolean {
        val filters = activityElement.getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as? Element ?: continue
            val children = filter.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j) as? Element ?: continue
                if (child.tagName == TAG_ACTION &&
                    child.getAttribute(ATTR_NAME) == "WATCH_FACE_EDITOR") {
                    return true
                }
            }
        }
        return false
    }

    private fun hasWearableConfigurationCategory(activityElement: Element): Boolean {
        val filters = activityElement.getElementsByTagName(TAG_INTENT_FILTER)
        for (i in 0 until filters.length) {
            val filter = filters.item(i) as? Element ?: continue
            val children = filter.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j) as? Element ?: continue
                if (child.tagName == TAG_CATEGORY &&
                    child.getAttribute(ATTR_NAME) == "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION") {
                    return true
                }
            }
        }
        return false
    }

    override fun afterCheckFile(context: Context) {
        if (servicesWithConfigAction.isEmpty()) return

        val minSdk = context.mainProject.minSdkVersion
        val requireCategory = minSdk < 30

        val validActivities = activitiesWithEditorFilter.filter { activity ->
            if (requireCategory) {
                hasWearableConfigurationCategory(activity)
            } else {
                true
            }
        }

        if (validActivities.isEmpty()) {
            for (service in servicesWithConfigAction) {
                context.report(
                    ISSUE,
                    service,
                    context.getLocation(service),
                    "Missing watch face configuration activity for WATCH_FACE_EDITOR"
                )
            }
        } else if (validActivities.size > 1) {
            for (activity in validActivities) {
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
        val ISSUE: Issue = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` metadata, \
                with the value `WATCH_FACE_EDITOR`, there should be an activity in the same package, \
                which has an intent filter for `WATCH_FACE_EDITOR` (with \
                com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).
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