package com.android.tools.lint.checks

import com.android.SdkConstants
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
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Detector for WearableActionDuplicate issue.
 *
 * If a watch face service defines `wearableConfigurationAction` metadata with value
 * `WATCH_FACE_EDITOR`, there should be an activity in the same package with an intent
 * filter for `WATCH_FACE_EDITOR` (and optionally `WEARABLE_CONFIGURATION` category if
 * minSdkVersion < 30).
 */
class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"
        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        val ISSUE: Issue = Issue.create(
            id = "WearableActionDuplicate",
            briefDescription = "Duplicate watch face configuration activities found",
            explanation = """
                If and only if a watch face service defines `wearableConfigurationAction` \
                metadata with the value `WATCH_FACE_EDITOR`, there should be an activity \
                in the same package which has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` \
                if minSdkVersion is less than 30).

                See https://developer.android.com/training/wearables/watch-faces/configuration
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearableConfigurationActionDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    // Data collected per manifest file
    private data class ServiceInfo(
        val element: Element,
        val location: Location,
        val packageName: String
    )

    private data class ActivityInfo(
        val element: Element,
        val location: Location,
        val packageName: String,
        val hasWatchFaceEditorAction: Boolean,
        val hasWearableConfigCategory: Boolean
    )

    // Per-file state
    private val servicesWithWatchFaceEditorMetadata = mutableListOf<ServiceInfo>()
    private val activitiesWithWatchFaceEditorAction = mutableListOf<ActivityInfo>()

    override fun getApplicableElements(): Collection<String> {
        return listOf(SdkConstants.TAG_SERVICE, SdkConstants.TAG_ACTIVITY)
    }

    override fun beforeCheckFile(context: Context) {
        servicesWithWatchFaceEditorMetadata.clear()
        activitiesWithWatchFaceEditorAction.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val manifestElement = getManifestElement(element) ?: return
        val packageName = manifestElement.getAttribute("package") ?: ""

        when (element.tagName) {
            SdkConstants.TAG_SERVICE -> {
                if (serviceHasWatchFaceEditorMetadata(element)) {
                    servicesWithWatchFaceEditorMetadata.add(
                        ServiceInfo(
                            element = element,
                            location = context.getLocation(element),
                            packageName = packageName
                        )
                    )
                }
            }
            SdkConstants.TAG_ACTIVITY -> {
                val (hasAction, hasCategory) = activityWatchFaceEditorInfo(element)
                if (hasAction) {
                    activitiesWithWatchFaceEditorAction.add(
                        ActivityInfo(
                            element = element,
                            location = context.getLocation(element),
                            packageName = packageName,
                            hasWatchFaceEditorAction = hasAction,
                            hasWearableConfigCategory = hasCategory
                        )
                    )
                }
            }
        }
    }

    override fun afterCheckFile(context: Context) {
        val minSdkVersion = context.mainProject.minSdkVersion.apiLevel

        // Case 1: Service has metadata but no matching activity
        for (service in servicesWithWatchFaceEditorMetadata) {
            val matchingActivities = activitiesWithWatchFaceEditorAction.filter {
                it.packageName == service.packageName
            }

            if (matchingActivities.isEmpty()) {
                // Missing activity entirely
                context.report(
                    issue = ISSUE,
                    location = service.location,
                    message = "Watch face service defines `wearableConfigurationAction` with " +
                        "`WATCH_FACE_EDITOR` but no activity with an intent filter for " +
                        "`WATCH_FACE_EDITOR` was found in the same package."
                )
            } else if (minSdkVersion < 30) {
                // Check that at least one activity also has WEARABLE_CONFIGURATION category
                val hasProperActivity = matchingActivities.any { it.hasWearableConfigCategory }
                if (!hasProperActivity) {
                    for (activity in matchingActivities) {
                        context.report(
                            issue = ISSUE,
                            location = activity.location,
                            message = "Activity with `WATCH_FACE_EDITOR` intent filter is missing " +
                                "the `$WEARABLE_CONFIGURATION_CATEGORY` category, " +
                                "which is required when minSdkVersion is less than 30."
                        )
                    }
                }
            }
        }

        // Case 2: Activity has WATCH_FACE_EDITOR action but no service with metadata
        if (servicesWithWatchFaceEditorMetadata.isEmpty()) {
            for (activity in activitiesWithWatchFaceEditorAction) {
                context.report(
                    issue = ISSUE,
                    location = activity.location,
                    message = "Activity has an intent filter for `WATCH_FACE_EDITOR` but no " +
                        "watch face service in the same package defines " +
                        "`wearableConfigurationAction` metadata with value `WATCH_FACE_EDITOR`."
                )
            }
        }
    }

    /**
     * Checks if a service element has the wearableConfigurationAction metadata set to
     * WATCH_FACE_EDITOR.
     */
    private fun serviceHasWatchFaceEditorMetadata(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == SdkConstants.TAG_META_DATA) {
                val metaData = child as Element
                val name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    .takeIf { it.isNotEmpty() }
                    ?: metaData.getAttribute(SdkConstants.ATTR_NAME)
                val value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)
                    .takeIf { it.isNotEmpty() }
                    ?: metaData.getAttribute(SdkConstants.ATTR_VALUE)

                if (name == WEARABLE_CONFIGURATION_ACTION_METADATA &&
                    value == WATCH_FACE_EDITOR_ACTION
                ) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns a pair of (hasWatchFaceEditorAction, hasWearableConfigCategory) for an activity.
     */
    private fun activityWatchFaceEditorInfo(activityElement: Element): Pair<Boolean, Boolean> {
        var hasAction = false
        var hasCategory = false

        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE &&
                child.nodeName == SdkConstants.TAG_INTENT_FILTER
            ) {
                val intentFilter = child as Element
                var filterHasAction = false
                var filterHasCategory = false

                val filterChildren = intentFilter.childNodes
                for (j in 0 until filterChildren.length) {
                    val filterChild = filterChildren.item(j)
                    if (filterChild.nodeType == Node.ELEMENT_NODE) {
                        val filterChildElement = filterChild as Element
                        when (filterChildElement.tagName) {
                            "action" -> {
                                val actionName = filterChildElement
                                    .getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                                    .takeIf { it.isNotEmpty() }
                                    ?: filterChildElement.getAttribute(SdkConstants.ATTR_NAME)
                                if (actionName == WATCH_FACE_EDITOR_ACTION) {
                                    filterHasAction = true
                                }
                            }
                            "category" -> {
                                val categoryName = filterChildElement
                                    .getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                                    .takeIf { it.isNotEmpty() }
                                    ?: filterChildElement.getAttribute(SdkConstants.ATTR_NAME)
                                if (categoryName == WEARABLE_CONFIGURATION_CATEGORY) {
                                    filterHasCategory = true
                                }
                            }
                        }
                    }
                }

                if (filterHasAction) {
                    hasAction = true
                    if (filterHasCategory) {
                        hasCategory = true
                    }
                }
            }
        }

        return Pair(hasAction, hasCategory)
    }

    /**
     * Walks up the DOM tree to find the manifest element.
     */
    private fun getManifestElement(element: Element): Element? {
        var node: Node? = element
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == SdkConstants.TAG_MANIFEST) {
                return node as Element
            }
            node = node.parentNode
        }
        return null
    }
}