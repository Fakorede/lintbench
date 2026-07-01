package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
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

class WearableConfigurationActionDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WearableConfigurationActionDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "WearableConfigurationAction",
            briefDescription = "Wear configuration action metadata must match an activity",
            explanation = """
                Only when a watch face service defines `wearableConfigurationAction` metadata with \
                the value `WATCH_FACE_EDITOR`, there should be an activity in the same package \
                which has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if \
                `minSdkVersion` is less than 30).
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val WATCH_FACE_EDITOR_ACTION =
            "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"

        private const val WEARABLE_CONFIGURATION_CATEGORY =
            "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

        private const val WEARABLE_CONFIGURATION_ACTION_METADATA =
            "com.google.android.wearable.watchface.wearableConfigurationAction"

        private const val MIN_SDK_FOR_NO_CATEGORY = 30
    }

    // Data class to hold information about a service that has the wearableConfigurationAction metadata
    private data class ServiceWithConfig(
        val element: Element,
        val location: Location,
        val packageName: String,
    )

    // Data class to hold information about an activity with WATCH_FACE_EDITOR intent filter
    private data class ActivityWithEditor(
        val element: Element,
        val hasWearableConfigCategory: Boolean,
        val packageName: String,
    )

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_SERVICE, TAG_ACTIVITY)
    }

    private val servicesWithConfig = mutableListOf<ServiceWithConfig>()
    private val activitiesWithEditor = mutableListOf<ActivityWithEditor>()

    override fun visitElement(context: XmlContext, element: Element) {
        val packageName = context.mainProject.`package` ?: return

        when (element.tagName) {
            TAG_SERVICE -> {
                // Check if this service has wearableConfigurationAction metadata with WATCH_FACE_EDITOR value
                if (serviceHasWatchFaceEditorConfig(element)) {
                    servicesWithConfig.add(
                        ServiceWithConfig(
                            element = element,
                            location = context.getLocation(element),
                            packageName = packageName,
                        )
                    )
                }
            }
            TAG_ACTIVITY -> {
                // Check if this activity has an intent filter for WATCH_FACE_EDITOR
                val (hasEditor, hasCategory) = activityHasWatchFaceEditorFilter(element)
                if (hasEditor) {
                    activitiesWithEditor.add(
                        ActivityWithEditor(
                            element = element,
                            hasWearableConfigCategory = hasCategory,
                            packageName = packageName,
                        )
                    )
                }
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        val minSdkVersion = context.mainProject.minSdkVersion.apiLevel

        for (service in servicesWithConfig) {
            // Find matching activity in same package
            val matchingActivities = activitiesWithEditor.filter { it.packageName == service.packageName }

            if (matchingActivities.isEmpty()) {
                // No activity with WATCH_FACE_EDITOR intent filter found
                context.report(
                    issue = ISSUE,
                    location = service.location,
                    message = "A watch face service with `wearableConfigurationAction` metadata " +
                            "must have a corresponding activity with an intent filter for " +
                            "`$WATCH_FACE_EDITOR_ACTION`",
                )
            } else if (minSdkVersion < MIN_SDK_FOR_NO_CATEGORY) {
                // Check that at least one activity has the WEARABLE_CONFIGURATION category
                val hasActivityWithCategory = matchingActivities.any { it.hasWearableConfigCategory }
                if (!hasActivityWithCategory) {
                    context.report(
                        issue = ISSUE,
                        location = service.location,
                        message = "A watch face service with `wearableConfigurationAction` metadata " +
                                "must have a corresponding activity with an intent filter for " +
                                "`$WATCH_FACE_EDITOR_ACTION` and category " +
                                "`$WEARABLE_CONFIGURATION_CATEGORY` " +
                                "(required when `minSdkVersion` < $MIN_SDK_FOR_NO_CATEGORY)",
                    )
                }
            }
        }
    }

    /**
     * Returns true if the given service element has a meta-data child with
     * name = WEARABLE_CONFIGURATION_ACTION_METADATA and value = WATCH_FACE_EDITOR_ACTION.
     */
    private fun serviceHasWatchFaceEditorConfig(serviceElement: Element): Boolean {
        val children = serviceElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_META_DATA) {
                val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val value = child.getAttributeNS(ANDROID_URI, ATTR_VALUE)
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
     * Returns a pair of (hasWatchFaceEditorAction, hasWearableConfigCategory)
     * for the given activity element.
     */
    private fun activityHasWatchFaceEditorFilter(activityElement: Element): Pair<Boolean, Boolean> {
        val children = activityElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == TAG_INTENT_FILTER) {
                val (hasAction, hasCategory) = intentFilterHasWatchFaceEditor(child)
                if (hasAction) {
                    return Pair(true, hasCategory)
                }
            }
        }
        return Pair(false, false)
    }

    /**
     * Returns a pair of (hasWatchFaceEditorAction, hasWearableConfigCategory)
     * for the given intent-filter element.
     */
    private fun intentFilterHasWatchFaceEditor(intentFilter: Element): Pair<Boolean, Boolean> {
        var hasAction = false
        var hasCategory = false

        val children = intentFilter.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            when (child.tagName) {
                TAG_ACTION -> {
                    val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == WATCH_FACE_EDITOR_ACTION) {
                        hasAction = true
                    }
                }
                TAG_CATEGORY -> {
                    val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (name == WEARABLE_CONFIGURATION_CATEGORY) {
                        hasCategory = true
                    }
                }
            }
        }

        return Pair(hasAction, hasCategory)
    }
}